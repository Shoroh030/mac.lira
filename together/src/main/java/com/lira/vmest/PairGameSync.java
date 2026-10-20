package com.lira.vmest;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small authenticated Supabase REST client for couple-scoped game sessions. */
final class PairGameSync {
    private final SharedPreferences prefs;
    PairGameSync(Context context) { prefs=context.getSharedPreferences("vmeste_data",Context.MODE_PRIVATE); }

    String coupleId() { return prefs.getString("sb_couple", ""); }
    String userId() { return prefs.getString("sb_user", ""); }
    boolean ready() { return !coupleId().isEmpty() && !userId().isEmpty() && !prefs.getString("sb_access", "").isEmpty(); }

    JSONObject latest(String type) throws Exception {
        String path="/rest/v1/couple_games?select=id,couple_id,game_type,host_id,status,state,version&couple_id=eq."+coupleId()+
                "&game_type=eq."+type+"&status=in.(waiting,active)&order=updated_at.desc&limit=1";
        JSONArray rows=(JSONArray)request("GET",path,null);
        return rows.length()==0?null:rows.getJSONObject(0);
    }

    void create(String id,String type,JSONObject state) throws Exception {
        JSONObject row=new JSONObject();row.put("id",id);row.put("couple_id",coupleId());row.put("game_type",type);
        row.put("host_id",userId());row.put("status","waiting");row.put("state",state);row.put("version",1);
        request("POST","/rest/v1/couple_games",row);
    }

    JSONObject update(String id,long version,JSONObject state,String status) throws Exception {
        JSONObject args=new JSONObject();args.put("target_id",id);args.put("expected_version",version);
        args.put("new_state",state);args.put("new_status",status);
        Object result=request("POST","/rest/v1/rpc/update_couple_game",args);
        JSONArray rows=result instanceof JSONArray?(JSONArray)result:new JSONArray();
        if(rows.length()==0)throw new Exception("Партия уже обновилась на другом телефоне. Обновляю поле…");
        return rows.getJSONObject(0);
    }

    JSONObject fetch(String id) throws Exception {
        JSONArray rows=(JSONArray)request("GET","/rest/v1/couple_games?select=id,couple_id,game_type,host_id,status,state,version&id=eq."+id+"&limit=1",null);
        return rows.length()==0?null:rows.getJSONObject(0);
    }

    private Object request(String method,String path,JSONObject body)throws Exception {
        String base=System.getenv("SUPABASE_URL");if(base==null||base.isEmpty())base=BuildConfig.SUPABASE_URL;
        String key=System.getenv("SUPABASE_PUBLISHABLE_KEY");if(key==null||key.isEmpty())key=BuildConfig.SUPABASE_PUBLISHABLE_KEY;
        String savedUrl=prefs.getString("sb_url","");if(!savedUrl.isEmpty())base=savedUrl;
        String savedKey=prefs.getString("sb_key","");if(!savedKey.isEmpty())key=savedKey;
        HttpURLConnection c=(HttpURLConnection)new URL(base.replaceAll("/+$","")+path).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(10000);c.setReadTimeout(12000);
        c.setRequestProperty("apikey",key);c.setRequestProperty("Authorization","Bearer "+prefs.getString("sb_access",""));
        c.setRequestProperty("Accept","application/json");
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream out=c.getOutputStream()){out.write(data);}}
        int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(stream!=null){byte[] buffer=new byte[4096];int n;while((n=stream.read(buffer))!=-1)bytes.write(buffer,0,n);stream.close();}
        String text=new String(bytes.toByteArray(),StandardCharsets.UTF_8);c.disconnect();
        if(status<200||status>=300)throw new Exception("Supabase HTTP "+status+": "+text);
        if(text.isEmpty())return new JSONObject();char first=text.charAt(0);
        if(first=='[')return new JSONArray(text);if(first=='{')return new JSONObject(text);return text;
    }
}
