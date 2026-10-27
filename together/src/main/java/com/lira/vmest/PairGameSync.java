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
    private final Context appContext;
    PairGameSync(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences("vmeste_data", Context.MODE_PRIVATE);
    }

    String coupleId() { return prefs.getString("sb_couple", ""); }
    String userId() { return prefs.getString("sb_user", ""); }
    boolean ready() { return !coupleId().isEmpty() && !userId().isEmpty() && !prefs.getString("sb_access", "").isEmpty(); }

    JSONObject latest(String type) throws Exception {
        String path="/rest/v1/couple_games?select=id,couple_id,game_type,host_id,status,state,version&couple_id=eq."+coupleId()+
                "&game_type=eq."+type+"&status=in.(waiting,active)&order=updated_at.desc&limit=1";
        Object resp=request("GET",path,null);
        JSONArray rows=resp instanceof JSONArray?(JSONArray)resp:new JSONArray();
        return rows.length()==0?null:rows.getJSONObject(0);
    }

    JSONArray waitingInvites() throws Exception {
        String path="/rest/v1/couple_games?select=id,game_type,host_id,status,state,updated_at&couple_id=eq."+coupleId()+
                "&host_id=neq."+userId()+"&status=eq.waiting&order=updated_at.desc&limit=20";
        Object resp=request("GET",path,null);
        return resp instanceof JSONArray?(JSONArray)resp:new JSONArray();
    }

    JSONArray history() throws Exception {
        final int pageSize=300;
        JSONArray all=new JSONArray();
        String couple = coupleId();
        String cursor = "";
        for(;;){
            String path="/rest/v1/couple_games?select=id,game_type,status,state&couple_id=eq."+couple+
                    "&status=eq.finished&order=id.asc&limit="+pageSize+
                    (cursor.isEmpty() ? "" : "&id=gt."+cursor);
            Object resp=request("GET",path,null);
            JSONArray page=resp instanceof JSONArray?(JSONArray)resp:new JSONArray();
            for(int i=0;i<page.length();i++)all.put(page.getJSONObject(i));
            if(page.length()<pageSize)break;
            JSONObject last = page.optJSONObject(page.length() - 1);
            String nextCursor = last == null ? "" : last.optString("id", "");
            if(nextCursor.isEmpty() || nextCursor.equals(cursor)) break;
            cursor = nextCursor;
        }
        return all;
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
        JSONObject updated;
        if(rows.length()==0){
            JSONObject current=fetch(id);
            JSONObject currentState=current==null?null:current.optJSONObject("state");
            if(current!=null&&GameScoreSyncPolicy.isIdempotentCompletionRetry(
                    status,current.optString("status"),state.toString(),
                    currentState==null?null:currentState.toString()))updated=current;
            else throw new Exception("Партия уже обновилась на другом телефоне. Откройте её заново и повторите ход.");
        }else updated=rows.getJSONObject(0);
        if ("finished".equals(status)
                && !GameScoreSyncScheduler.requestSoon(appContext)) {
            android.util.Log.w("GameScoreSync", "Could not schedule score refresh");
        }
        return updated;
    }

    JSONObject fetch(String id) throws Exception {
        Object resp=request("GET","/rest/v1/couple_games?select=id,couple_id,game_type,host_id,status,state,version&id=eq."+id+"&limit=1",null);
        JSONArray rows=resp instanceof JSONArray?(JSONArray)resp:new JSONArray();
        return rows.length()==0?null:rows.getJSONObject(0);
    }

    private Object request(String method,String path,JSONObject body)throws Exception {
        return request(method, path, body, true, true);
    }

    private Object request(String method,String path,JSONObject body,
            boolean authenticated,boolean retryAuth)throws Exception {
        String base=prefs.getString("sb_url","");
        if(base==null||base.isEmpty())base=BuildConfig.SUPABASE_URL;
        String key=prefs.getString("sb_key","");
        if(key==null||key.isEmpty())key=BuildConfig.SUPABASE_PUBLISHABLE_KEY;
        base=base.replaceAll("/+$","");
        if(base.isEmpty())throw new Exception("URL Supabase не настроен");
        HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();
        c.setRequestMethod(method);c.setConnectTimeout(10000);c.setReadTimeout(12000);
        c.setRequestProperty("apikey",key);
        if(authenticated)c.setRequestProperty("Authorization","Bearer "+prefs.getString("sb_access",""));
        c.setRequestProperty("Accept","application/json");
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream out=c.getOutputStream()){out.write(data);}}
        int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(stream!=null){byte[] buffer=new byte[4096];int n;while((n=stream.read(buffer))!=-1)bytes.write(buffer,0,n);stream.close();}
        String text=new String(bytes.toByteArray(),StandardCharsets.UTF_8);c.disconnect();
        if(status==401&&authenticated&&retryAuth&&!prefs.getString("sb_refresh","").isEmpty()){
            refreshSession();
            return request(method,path,body,true,false);
        }
        if(status<200||status>=300)throw new Exception("Supabase HTTP "+status+": "+text);
        if(text.isEmpty())return new JSONObject();char first=text.charAt(0);
        if(first=='[')return new JSONArray(text);if(first=='{')return new JSONObject(text);return text;
    }

    private void refreshSession()throws Exception{
        JSONObject args=new JSONObject();args.put("refresh_token",prefs.getString("sb_refresh",""));
        Object response=request("POST","/auth/v1/token?grant_type=refresh_token",args,false,false);
        JSONObject result=response instanceof JSONObject?(JSONObject)response:new JSONObject();
        String access=result.optString("access_token","");
        if(access.isEmpty())throw new Exception("Войдите в аккаунт снова");
        JSONObject user=result.optJSONObject("user");
        prefs.edit().putString("sb_access",access)
                .putString("sb_refresh",result.optString("refresh_token",prefs.getString("sb_refresh","")))
                .putString("sb_user",user==null?userId():user.optString("id",userId()))
                .apply();
    }
}
