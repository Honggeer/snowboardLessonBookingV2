package com.geer.snowboard.v2.coachprofile.adapter.out.persistence;
import com.geer.snowboard.v2.coachprofile.application.port.out.ProfileStore;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class JdbcProfileStore implements ProfileStore {
    private record PageRecord(String owner,long draftVersion,long publishedVersion,String draft,String published) {}
    private final JdbcTemplate jdbc;private final JsonMapper json;
    public JdbcProfileStore(JdbcTemplate jdbc,JsonMapper json){this.jdbc=jdbc;this.json=json;}
    public Snapshot read(){return query(false);}
    public Snapshot lock(String owner){
        jdbc.update("INSERT IGNORE INTO coach_profile_page (id,owner_id,draft_json,published_json) VALUES (1,?, ?, '{}')",owner,"{\"displayName\":\"GEER\"}");
        return query(true);
    }
    private Snapshot query(boolean lock){return jdbc.query("SELECT * FROM coach_profile_page WHERE id=1"+(lock?" FOR UPDATE":""),(rs,n)->new PageRecord(rs.getString("owner_id"),rs.getLong("draft_version"),rs.getLong("published_version"),rs.getString("draft_json"),rs.getString("published_json"))).stream().findFirst().map(r->new Snapshot(r.owner,r.draftVersion,r.publishedVersion,decode(r.draft),decode(r.published))).orElse(null);}
    private Map<String,String> decode(String value){return json.readValue(value,new TypeReference<Map<String,String>>(){});}
    public void save(long version,Map<String,String> content){jdbc.update("UPDATE coach_profile_page SET draft_version=?,draft_json=? WHERE id=1",version,json.writeValueAsString(content));}
    public void publish(long version,Map<String,String> content){jdbc.update("UPDATE coach_profile_page SET published_version=?,published_json=? WHERE id=1",version,json.writeValueAsString(content));}
    public Publication publication(String owner,String key){return jdbc.query("SELECT fingerprint,published_version FROM coach_profile_publish_request WHERE owner_id=? AND request_key=?",(rs,n)->new Publication(rs.getString(1),rs.getLong(2)),owner,key).stream().findFirst().orElse(null);}
    public void recordPublication(String owner,String key,String fingerprint,long version){jdbc.update("INSERT INTO coach_profile_publish_request (owner_id,request_key,fingerprint,published_version) VALUES (?,?,?,?)",owner,key,fingerprint,version);}
}
