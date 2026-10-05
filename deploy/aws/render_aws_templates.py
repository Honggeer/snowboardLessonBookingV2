"""Render JSON API inputs for review. Does not call AWS or create resources."""
import argparse
import json
import re
from pathlib import Path


def statement(actions, resources, **extra):
    return {"Effect": "Allow", "Action": actions, "Resource": resources} | extra


def policy(statements):
    return {"Version": "2012-10-17", "Statement": statements}


def render(p):
    patterns = {"account_id": r"[0-9]{12}", "instance_id": r"i-[0-9a-f]{17}",
        "origin": r"https://[a-z0-9.-]+", "distribution_id": r"[A-Z0-9]+",
        "oidc_subject": r"repo:[A-Za-z0-9_.-]+(?:@[0-9]+)?/[A-Za-z0-9_.-]+(?:@[0-9]+)?:environment:production"}
    for key, pattern in patterns.items():
        if not re.fullmatch(pattern, p.get(key, "")):
            raise ValueError("invalid template parameter: " + key)
    buckets = [p.get(key, "") for key in ["staging_bucket", "frozen_bucket", "operations_bucket"]]
    if len(set(buckets)) != 3 or any(not re.fullmatch(r"[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]", bucket) for bucket in buckets):
        raise ValueError("three distinct valid private buckets required")
    account = p["account_id"]
    prefix = "arn:aws:"
    ecr = [prefix + "ecr:ca-central-1:" + account + ":repository/snowboard-v2-" + kind for kind in ["backend", "frontend"]]
    staging, frozen, operations = ["arn:aws:s3:::" + bucket for bucket in buckets]
    document_arn = prefix + "ssm:ca-central-1:" + account + ":document/snowboard-v2-release"
    instance_arn = prefix + "ec2:ca-central-1:" + account + ":instance/" + p["instance_id"]
    ci = policy([
        statement(["ecr:GetAuthorizationToken"], "*"),
        statement(["ecr:BatchCheckLayerAvailability", "ecr:InitiateLayerUpload", "ecr:UploadLayerPart",
                   "ecr:CompleteLayerUpload", "ecr:PutImage", "ecr:BatchGetImage", "ecr:DescribeImages", "ecr:GetDownloadUrlForLayer"], ecr),
        statement(["s3:PutObject"], operations + "/releases/*"),
        statement(["ssm:SendCommand"], [document_arn, instance_arn]),
        statement(["ssm:GetCommandInvocation"], "*")])
    instance = policy([
        statement(["ecr:GetAuthorizationToken"], "*"),
        statement(["ecr:BatchCheckLayerAvailability", "ecr:BatchGetImage", "ecr:GetDownloadUrlForLayer"], ecr),
        statement(["s3:GetBucketVersioning"], staging),
        statement(["s3:ListBucket", "s3:ListBucketVersions"], staging,
                  Condition={"StringLike": {"s3:prefix": ["staging/*"]}}),
        statement(["s3:ListBucket", "s3:ListBucketVersions"], frozen,
                  Condition={"StringLike": {"s3:prefix": ["frozen/*"]}}),
        statement(["s3:GetObject", "s3:GetObjectVersion", "s3:PutObject", "s3:DeleteObject", "s3:DeleteObjectVersion"],
                  [staging + "/staging/*", frozen + "/frozen/*"]),
        statement(["s3:GetObject"], operations + "/releases/*"),
        statement(["s3:PutObject", "s3:GetObject"], operations + "/db-backups/*"),
        statement(["s3:ListBucket"], operations,
                  Condition={"StringLike": {"s3:prefix": ["db-backups/*"]}})])
    trust = {"Version": "2012-10-17", "Statement": [{"Effect": "Allow", "Action": "sts:AssumeRoleWithWebIdentity",
        "Principal": {"Federated": prefix + "iam::" + account + ":oidc-provider/token.actions.githubusercontent.com"},
        "Condition": {"StringEquals": {"token.actions.githubusercontent.com:aud": "sts.amazonaws.com",
                                      "token.actions.githubusercontent.com:sub": p["oidc_subject"]}}}]}
    ssm_parameters = {name: {"type": "String", "interpolationType": "ENV_VAR", "allowedPattern": pattern}
        for name, pattern in [("CommitSha", "^[0-9a-f]{40}$"), ("BundleSha256", "^[0-9a-f]{64}$"),
                              ("BackendDigest", "^sha256:[0-9a-f]{64}$"), ("FrontendDigest", "^sha256:[0-9a-f]{64}$")]}
    ssm = {"schemaVersion": "2.2", "description": "Fixed snowboard v2 publication entrypoint",
           "parameters": ssm_parameters, "mainSteps": [{"action": "aws:runShellScript", "name": "publish",
           "inputs": {"timeoutSeconds": "900", "runCommand": [
               'set -eu',
               'python3 /opt/snowboard-v2/bin/dispatch.py --sha "$SSM_CommitSha" --bundle-sha256 "$SSM_BundleSha256" --backend-digest "$SSM_BackendDigest" --frontend-digest "$SSM_FrontendDigest"'
           ]}}]}
    oac = {"OriginAccessControlConfig": {"Name": "snowboard-v2-media", "Description": "Private frozen media",
             "SigningProtocol": "sigv4", "SigningBehavior": "always", "OriginAccessControlOriginType": "s3"}}
    frozen_policy = policy([statement(["s3:GetObject"], frozen + "/frozen/*",
        Principal={"Service": "cloudfront.amazonaws.com"},
        Condition={"StringEquals": {"AWS:SourceArn": prefix + "cloudfront::" + account + ":distribution/" + p["distribution_id"]}})])
    cors = {"CORSRules": [{"AllowedOrigins": [p["origin"]], "AllowedMethods": ["PUT", "HEAD"],
                          "AllowedHeaders": ["content-type", "x-amz-*"], "ExposeHeaders": ["ETag", "x-amz-version-id"], "MaxAgeSeconds": 600}]}
    privacy = {"PublicAccessBlockConfiguration": {key: True for key in ["BlockPublicAcls", "IgnorePublicAcls", "BlockPublicPolicy", "RestrictPublicBuckets"]}}
    encryption = {"ServerSideEncryptionConfiguration": {"Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}}]}}
    lifecycle = {"Rules": [
        {"ID": "seven-day-backups", "Status": "Enabled", "Filter": {"Prefix": "db-backups/"}, "Expiration": {"Days": 7},
         "NoncurrentVersionExpiration": {"NoncurrentDays": 1}, "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 1}},
        {"ID": "backup-delete-markers", "Status": "Enabled", "Filter": {"Prefix": "db-backups/"},
         "Expiration": {"ExpiredObjectDeleteMarker": True}}]}
    ecr_lifecycle = {"rules": [{"rulePriority": 1, "description": "Remove unused untagged layers after 7 days",
        "selection": {"tagStatus": "untagged", "countType": "sinceImagePushed", "countUnit": "days", "countNumber": 7},
        "action": {"type": "expire"}}]}
    # IDs are filled after creating OAC/public key/key group; no secret key here.
    oac_id = p.get("oac_id", "EEXAMPLEOAC")
    group_id = p.get("key_group_id", "00000000-0000-0000-0000-000000000000")
    public_key_id = p.get("public_key_id", "KEXAMPLEPUBLICKEY")
    distribution = {"DistributionConfig": {"CallerReference": "snowboard-v2-" + account,
        "Comment": "Private signed frozen media", "Enabled": True, "PriceClass": "PriceClass_100",
        "HttpVersion": "http2", "IsIPV6Enabled": True,
        "Origins": {"Quantity": 1, "Items": [{"Id": "frozen-media", "DomainName": buckets[1] + ".s3.ca-central-1.amazonaws.com",
            "OriginPath": "", "S3OriginConfig": {"OriginAccessIdentity": ""}, "OriginAccessControlId": oac_id}]},
        "DefaultCacheBehavior": {"TargetOriginId": "frozen-media", "ViewerProtocolPolicy": "https-only",
            "AllowedMethods": {"Quantity": 2, "Items": ["GET", "HEAD"], "CachedMethods": {"Quantity": 2, "Items": ["GET", "HEAD"]}},
            "TrustedKeyGroups": {"Enabled": True, "Quantity": 1, "Items": [group_id]}, "Compress": True,
            "CachePolicyId": "658327ea-f89d-4fab-a63d-7e88639e58f6"},
        "ViewerCertificate": {"CloudFrontDefaultCertificate": True},
        "Restrictions": {"GeoRestriction": {"RestrictionType": "none", "Quantity": 0}}}}
    result = {"ci-trust.json": trust, "ci-permissions.json": ci, "ec2-permissions.json": instance,
            "ssm-document.json": ssm, "oac.json": oac, "frozen-bucket-policy.json": frozen_policy,
            "staging-cors.json": cors, "bucket-privacy.json": privacy, "bucket-encryption.json": encryption,
            "bucket-versioning.json": {"VersioningConfiguration": {"Status": "Enabled"}},
            "operations-lifecycle.json": lifecycle, "ecr-lifecycle.json": ecr_lifecycle,
            "staging-lifecycle.json": {"Rules": [{"ID": "incomplete-uploads", "Status": "Enabled", "Filter": {"Prefix": "staging/"},
                 "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 7}}]},
            "cloudfront-key-group.json": {"KeyGroupConfig": {"Name": "snowboard-v2-media", "Comment": "Verified viewer signatures", "Items": [public_key_id]}},
            "cloudfront-distribution.json": distribution}
    for kind, bucket in zip(["staging", "frozen", "operations"], [staging, frozen, operations]):
        deny = {"Effect": "Deny", "Principal": "*", "Action": "s3:*", "Resource": [bucket, bucket + "/*"],
                "Condition": {"Bool": {"aws:SecureTransport": "false"}}}
        if kind == "frozen":
            result["frozen-bucket-policy.json"]["Statement"].append(deny)
        else:
            result[kind + "-bucket-policy.json"] = policy([deny])
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--parameters", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)
    for name, content in render(json.loads(Path(args.parameters).read_text())).items():
        (output / name).write_text(json.dumps(content, indent=2) + "\n")
    print("Rendered review templates only; no AWS operations executed")
