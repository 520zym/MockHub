package com.mockhub.mock.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Compatibility proof is an exact SOAP field, never a substring anywhere in a response/snapshot. */
@Repository
public class WsdlReferenceRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public WsdlReferenceRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public boolean hasLegacyReference(String teamId,String name){
        for(String config:jdbc.queryForList("SELECT soap_config FROM api_definition WHERE team_id=? AND UPPER(type)='SOAP' AND soap_config IS NOT NULL",String.class,teamId))
            if(matches(config,name))return true;
        for(String snapshot:jdbc.queryForList("SELECT snapshot_json FROM api_revision WHERE team_id=?",String.class,teamId)){
            try{
                JsonNode root=json.readTree(snapshot),config=root.path("config");
                if(root.path("schemaVersion").asInt()==1 && config.isObject() && "SOAP".equalsIgnoreCase(config.path("type").asText()) && matches(config.get("soap_config"),name))return true;
            }catch(Exception ignored){ /* Malformed legacy data is not ownership evidence. */ }
        }
        return false;
    }
    private boolean matches(String value,String name){try{JsonNode config=value==null?null:json.readTree(value);return config!=null && config.isObject() && config.path("wsdlFileName").isTextual() && name.equals(config.get("wsdlFileName").asText());}catch(Exception ignored){return false;}}
    private boolean matches(JsonNode config,String name){
        try{
            if(config!=null && config.isTextual())config=json.readTree(config.asText());
            return config!=null && config.isObject() && config.path("wsdlFileName").isTextual() && name.equals(config.get("wsdlFileName").asText());
        }catch(Exception ignored){return false;}
    }
}
