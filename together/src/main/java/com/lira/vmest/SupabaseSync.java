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

/** Small REST bridge; it only uses the public client key and the signed-in user's JWT. */
final class SupabaseSync {
    private final SharedPreferences prefs;
    SupabaseSync(Context context) { prefs=context.getSharedPreferences("vmeste_data",Context.MODE_PRIVATE); }

    boolean configured(){return !url().isEmpty()&&!key().isEmpty();}
    boolean signedIn(){return !prefs.getString("sb_access","").isEmpty();}
    String coupleId(){return prefs.getString("sb_couple","");}
    String inviteCode(){return prefs.getString("sb_invite","");}
    String url(){String saved=prefs.getString("sb_url","");return (saved.isEmpty()?BuildConfig.SUPABASE_URL:saved).replaceAll("/+$","");}
    String key(){String saved=prefs.getString("sb_key","");return saved.isEmpty()?BuildConfig.SUPABASE_PUBLISHABLE_KEY:saved;}
    String userId(){return prefs.getString("sb_user","");}

    void saveProject(String projectUrl,String publishableKey){
        prefs.edit().putString("sb_url",projectUrl.trim().replaceAll("/+$", ""))
                .putString("sb_key",publishableKey.trim()).apply();
    }

    String authenticate(String email,String password,boolean signUp)throws Exception{
        JSONObject body=new JSONObject();body.put("email",email);body.put("password",password);
        JSONObject result=request("POST",signUp?"/auth/v1/signup":"/auth/v1/token?grant_type=password",body,false);
        String access=result.optString("access_token","");
        if(access.isEmpty())return "EMAIL_CONFIRMATION_REQUIRED";
        JSONObject user=result.optJSONObject("user");
        prefs.edit().putString("sb_access",access).putString("sb_refresh",result.optString("refresh_token",""))
                .putString("sb_user",user==null?"":user.optString("id", "")).apply();
        return "OK";
    }

    JSONObject createCouple(String name)throws Exception{
        JSONObject args=new JSONObject();args.put("couple_name",name);
        Object response=requestValue("POST","/rest/v1/rpc/create_couple",args,true);
        JSONObject couple=response instanceof JSONArray?((JSONArray)response).getJSONObject(0):(JSONObject)response;
        prefs.edit().putString("sb_couple",couple.getString("couple_id"))
                .putString("sb_invite",couple.getString("invite_code")).apply();
        return couple;
    }

    String joinCouple(String code)throws Exception{
        JSONObject args=new JSONObject();args.put("code",code.trim());
        Object response=requestValue("POST","/rest/v1/rpc/join_couple",args,true);
        String id=response instanceof String?(String)response:String.valueOf(response);
        id=id.replace("\"","").trim();prefs.edit().putString("sb_couple",id).putString("sb_invite",code.trim().toUpperCase()).apply();return id;
    }

    void upsert(String keyName,JSONObject localItem)throws Exception{
        String couple=coupleId();if(couple.isEmpty())return;
        String itemId=localItem.optString("id","");if(itemId.isEmpty())return;
        if("messages".equals(keyName)){
            JSONObject row=new JSONObject();row.put("id",itemId);row.put("couple_id",couple);row.put("body",localItem.optString("title"));
            request("POST","/rest/v1/couple_messages?on_conflict=id",row,true,true);
        }else{
            String kind=kindFor(keyName);if(kind==null)return;
            JSONObject payload=new JSONObject();payload.put("title",localItem.optString("title"));
            payload.put("detail",localItem.optString("detail",""));payload.put("done",localItem.optBoolean("done",false));
            if(keyName.startsWith("day_"))payload.put("date",keyName.substring(4));
            JSONObject row=new JSONObject();row.put("id",itemId);row.put("couple_id",couple);row.put("kind",kind);row.put("payload",payload);
            request("POST","/rest/v1/shared_items?on_conflict=id",row,true,true);
        }
    }

    JSONArray fetchItems()throws Exception{
        String couple=coupleId();
        Object items=requestValue("GET","/rest/v1/shared_items?select=*&couple_id=eq."+couple+"&order=created_at.asc",null,true);
        Object messages=requestValue("GET","/rest/v1/couple_messages?select=*&couple_id=eq."+couple+"&order=created_at.asc",null,true);
        JSONArray merged=new JSONArray();JSONArray a=(JSONArray)items,b=(JSONArray)messages;
        for(int i=0;i<a.length();i++)merged.put(a.getJSONObject(i));
        for(int i=0;i<b.length();i++)merged.put(b.getJSONObject(i));
        return merged;
    }

    void upsertMovieRoom(JSONObject room)throws Exception{
        String couple=coupleId();if(couple.isEmpty())return;
        JSONObject payload=new JSONObject();payload.put("title",room.optString("title","Вечер кино"));
        payload.put("url",room.optString("url",""));payload.put("status",room.optString("status","ready"));
        payload.put("updated",room.optLong("updated",System.currentTimeMillis()));
        JSONObject row=new JSONObject();row.put("id",room.getString("id"));row.put("couple_id",couple);row.put("kind","movie_room");row.put("payload",payload);
        request("POST","/rest/v1/shared_items?on_conflict=id",row,true,true);
    }

    JSONObject fetchMovieRoom()throws Exception{
        String couple=coupleId();if(couple.isEmpty())return null;
        Object response=requestValue("GET","/rest/v1/shared_items?select=id,payload&couple_id=eq."+couple+"&kind=eq.movie_room&order=updated_at.desc&limit=1",null,true);
        JSONArray rows=(JSONArray)response;if(rows.length()==0)return null;
        JSONObject row=rows.getJSONObject(0),payload=row.optJSONObject("payload");if(payload==null)return null;
        payload.put("id",row.optString("id"));return payload;
    }

    void createWatchRoom(String roomId,String title)throws Exception{
        JSONObject row=new JSONObject();row.put("id",roomId);row.put("couple_id",coupleId());
        row.put("host_id",userId());row.put("title",title);
        request("POST","/rest/v1/watch_rooms",row,true);
    }

    JSONObject getWatchRoom(String roomId)throws Exception{
        JSONArray rooms=(JSONArray)requestValue("GET","/rest/v1/watch_rooms?select=id,couple_id,host_id,title,state&id=eq."+roomId+"&limit=1",null,true);
        return rooms.length()==0?null:rooms.getJSONObject(0);
    }

    void sendWatchSignal(String roomId,String kind,JSONObject payload)throws Exception{
        JSONObject row=new JSONObject();row.put("room_id",roomId);row.put("couple_id",coupleId());
        row.put("sender_id",userId());row.put("kind",kind);row.put("payload",payload);
        request("POST","/rest/v1/watch_signals",row,true);
    }

    JSONArray fetchWatchSignals(String roomId,long afterId)throws Exception{
        return (JSONArray)requestValue("GET","/rest/v1/watch_signals?select=id,sender_id,kind,payload&room_id=eq."+roomId+
                "&id=gt."+afterId+"&order=id.asc&limit=100",null,true);
    }

    void saveProfile(JSONObject profile)throws Exception{
        JSONObject row=new JSONObject();row.put("user_id",userId());row.put("couple_id",coupleId());
        row.put("display_name",profile.optString("display_name"));row.put("avatar",profile.optString("avatar","♡"));
        row.put("bio",profile.optString("bio",""));
        request("POST","/rest/v1/couple_profiles?on_conflict=user_id",row,true,true);
    }

    JSONArray fetchProfiles()throws Exception{
        return (JSONArray)requestValue("GET","/rest/v1/couple_profiles?select=user_id,display_name,avatar,bio&couple_id=eq."+coupleId(),null,true);
    }

    void storeRemote(JSONObject row,SharedPreferences target)throws Exception{
        if(row.has("body")){
            JSONObject entry=new JSONObject();entry.put("id",row.optString("id"));entry.put("title",row.optString("body"));
            entry.put("detail","Сообщение пары");entry.put("done",false);merge(target,"messages",entry);return;
        }
        JSONObject payload=row.optJSONObject("payload");if(payload==null)return;
        String key=keyFor(row.optString("kind"),payload.optString("date"));if(key==null)return;
        JSONObject entry=new JSONObject(payload.toString());entry.put("id",row.optString("id"));merge(target,key,entry);
    }

    private void merge(SharedPreferences target,String key,JSONObject entry)throws Exception{
        JSONArray old=new JSONArray(target.getString(key,"[]")),next=new JSONArray();String id=entry.optString("id");boolean found=false;
        for(int i=0;i<old.length();i++){JSONObject item=old.optJSONObject(i);if(item==null)continue;if(id.equals(item.optString("id"))){next.put(entry);found=true;}else next.put(item);}
        if(!found)next.put(entry);target.edit().putString(key,next.toString()).apply();
    }

    private String kindFor(String key){
        if("tasks".equals(key))return "task";if(key.startsWith("day_"))return "calendar_task";if("movie_room".equals(key))return "movie_room";
        if("events".equals(key))return "event";if("wishes".equals(key))return "wish";
        if("notes".equals(key))return "note";if("gifts".equals(key))return "gift";
        if("capsule".equals(key))return "time_capsule";if("votes".equals(key))return "vote";
        if("files".equals(key))return "file";if("loyalty".equals(key))return "loyalty_card";
        if("plans".equals(key))return "plan";return null;
    }
    private String keyFor(String kind,String date){
        switch(kind){case "task":return "tasks";case "calendar_task":return date.isEmpty()?null:"day_"+date;
            case "event":return "events";case "wish":return "wishes";case "note":return "notes";
            case "gift":return "gifts";case "time_capsule":return "capsule";case "vote":return "votes";
            case "file":return "files";case "loyalty_card":return "loyalty";case "plan":return "plans";default:return null;}
    }

    private JSONObject request(String method,String path,JSONObject body,boolean auth)throws Exception{return request(method,path,body,auth,false);}
    private JSONObject request(String method,String path,JSONObject body,boolean auth,boolean upsert)throws Exception{
        Object result=requestValue(method,path,body,auth,upsert);return result instanceof JSONObject?(JSONObject)result:new JSONObject();
    }
    private Object requestValue(String method,String path,JSONObject body,boolean auth)throws Exception{return requestValue(method,path,body,auth,false);}
    private Object requestValue(String method,String path,JSONObject body,boolean auth,boolean upsert)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url()+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(16000);
        c.setRequestProperty("apikey",key());c.setRequestProperty("Accept","application/json");
        if(auth)c.setRequestProperty("Authorization","Bearer "+prefs.getString("sb_access",""));
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            if(upsert)c.setRequestProperty("Prefer","resolution=merge-duplicates,return=minimal");
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
        int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(stream!=null){byte[] buf=new byte[4096];int n;while((n=stream.read(buf))!=-1)bytes.write(buf,0,n);stream.close();}
        String response=new String(bytes.toByteArray(),StandardCharsets.UTF_8);c.disconnect();
        if(status<200||status>=300)throw new Exception("HTTP "+status+": "+response);
        if(response.isEmpty())return new JSONObject();char first=response.charAt(0);
        if(first=='[')return new JSONArray(response);if(first=='{')return new JSONObject(response);if(first=='\"')return response.substring(1,response.length()-1);return response;
    }
}
