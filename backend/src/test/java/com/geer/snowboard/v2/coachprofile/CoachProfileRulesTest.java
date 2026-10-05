package com.geer.snowboard.v2.coachprofile;

import static org.assertj.core.api.Assertions.*;

import com.geer.snowboard.v2.coachprofile.domain.ProfileRules;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoachProfileRulesTest {
    private static final String IMAGE = "11111111-1111-1111-1111-111111111111";

    @Test void allowsWechatQrWithoutAWechatAccount() {
        assertThatCode(() -> ProfileRules.publishable(content(Map.of("wechatQrId", IMAGE)))).doesNotThrowAnyException();
    }

    @Test void allowsEitherSocialAccountWithoutALink() {
        for (String field : new String[]{"xhsAccount", "douyinAccount"})
            assertThatCode(() -> ProfileRules.publishable(content(Map.of(field, "自由昵称 @GEER")))).doesNotThrowAnyException();
    }

    @Test void acceptsSocialLinksAsOptionalTextWithoutPlatformOrFormatRules() {
        for (String field : new String[]{"xhsUrl", "douyinUrl"})
            for (String value : new String[]{"http://share.example.test/profile", "https://short.example.test/abc", "主页链接稍后补充", "javascript:alert(1)"}) {
                var normalized = content(Map.of(field, value));
                assertThat(normalized).containsEntry(field, value);
                assertThatCode(() -> ProfileRules.publishable(normalized)).doesNotThrowAnyException();
            }
    }

    @Test void ignoresTheLegacyWechatAccountWhenSaving() {
        assertThat(content(Map.of("wechatId", "legacy-wechat", "wechatQrId", IMAGE)))
            .doesNotContainKey("wechatId").containsEntry("wechatQrId", IMAGE);
    }

    @Test void retainsAccountAndLinkLengthLimits() {
        for (String field : new String[]{"xhsAccount", "douyinAccount", "xhsUrl", "douyinUrl"}) {
            int max = field.endsWith("Url") ? 2048 : 80;
            assertThat(content(Map.of(field, "字".repeat(max)))).containsEntry(field, "字".repeat(max));
            assertThatThrownBy(() -> content(Map.of(field, "字".repeat(max + 1)))).isInstanceOf(BusinessProblem.class);
        }
    }

    @Test void retainsOtherProfileAndMediaRequirements() {
        assertThatThrownBy(() -> ProfileRules.normalize(Map.of("heroId", "not-a-media-id"))).isInstanceOf(BusinessProblem.class);
        assertThatThrownBy(() -> ProfileRules.normalize(Map.of("unknown", "value"))).isInstanceOf(BusinessProblem.class);
        assertThatThrownBy(() -> ProfileRules.publishable(Map.of("displayName", "GEER", "tagline", "教学"))).isInstanceOf(BusinessProblem.class);
        assertThatThrownBy(() -> ProfileRules.publishable(content(Map.of("videoId", IMAGE)))).isInstanceOf(BusinessProblem.class);
        assertThatThrownBy(() -> ProfileRules.publishable(content(Map.of("casiLevel", "Level 1")))).isInstanceOf(BusinessProblem.class);
    }

    private Map<String, String> content(Map<String, String> extra) {
        var input = new HashMap<>(Map.of("displayName", "GEER", "tagline", "教学", "heroId", IMAGE));
        input.putAll(extra);
        return ProfileRules.normalize(input);
    }
}
