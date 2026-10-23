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
import java.util.UUID;

/** Small REST bridge; it only uses the public client key and the signed-in user's JWT. */
final class SupabaseSync {
    private final SharedPreferences prefs;
    private boolean projectChanged;
    SupabaseSync(Context context) {
        prefs=context.getSharedPreferences("vmeste_data",Context.MODE_PRIVATE);
        String target=normalizeUrl(BuildConfig.SUPABASE_URL);
        String previousBuild=prefs.getString("sb_build_url","");
        String savedUrl=normalizeUrl(prefs.getString("sb_url",""));
        boolean firstBuildBinding=previousBuild.isEmpty();
        boolean savedUsesPreviousBuild=savedUrl.isEmpty()||savedUrl.equals(normalizeUrl(previousBuild));
        boolean applyBuildBinding=firstBuildBinding||(!target.equals(normalizeUrl(previousBuild))&&savedUsesPreviousBuild);
        SharedPreferences.Editor editor=prefs.edit().putString("sb_build_url",target);
        if(applyBuildBinding){
            boolean oldProject=!savedUrl.isEmpty()&&!savedUrl.equals(target)||hasAccountBinding();
            editor.putString("sb_url",target).putString("sb_key",BuildConfig.SUPABASE_PUBLISHABLE_KEY);
            if(oldProject){
                clearAccountBinding(editor);
                projectChanged=true;
            }
        }
        editor.apply();
    }

    boolean configured(){return !url().isEmpty()&&!key().isEmpty();}
    boolean signedIn(){return !prefs.getString("sb_access","").isEmpty();}
    boolean ready(){return configured()&&signedIn()&&!coupleId().isEmpty();}
    String coupleId(){return prefs.getString("sb_couple","");}
    String inviteCode(){return prefs.getString("sb_invite","");}
    String url(){String saved=prefs.getString("sb_url","");return (saved.isEmpty()?BuildConfig.SUPABASE_URL:saved).replaceAll("/+$","");}
    String key(){String saved=prefs.getString("sb_key","");return saved.isEmpty()?BuildConfig.SUPABASE_PUBLISHABLE_KEY:saved;}
    String userId(){return prefs.getString("sb_user","");}

    void saveProject(String projectUrl,String publishableKey){
        String normalized=normalizeUrl(projectUrl);
        SharedPreferences.Editor editor=prefs.edit().putString("sb_url",normalized)
                .putString("sb_key",publishableKey.trim());
        if(!normalized.equals(url())){
            clearAccountBinding(editor);
            projectChanged=true;
        }
        editor.apply();
    }

    boolean projectChanged(){return projectChanged;}

    private static String normalizeUrl(String value){
        return value==null?"":value.trim().replaceAll("/+$","");
    }

    private static void clearAccountBinding(SharedPreferences.Editor editor){
        editor.remove("sb_access").remove("sb_refresh").remove("sb_user")
                .remove("sb_couple").remove("sb_invite").remove("pending_shared_sync")
                .remove("partner_user_id").remove("partner_name").remove("partner_avatar")
                .remove("partner_avatar_data").remove("partner_bio");
    }

    private boolean hasAccountBinding(){
        return !prefs.getString("sb_access","").isEmpty()
                ||!prefs.getString("sb_refresh","").isEmpty()
                ||!prefs.getString("sb_user","").isEmpty()
                ||!prefs.getString("sb_couple","").isEmpty()
                ||!prefs.getString("sb_invite","").isEmpty()
                ||!prefs.getString("pending_shared_sync","[]").trim().equals("[]");
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
        if("notes".equals(keyName))return;
        String itemId=localItem.optString("id","");if(itemId.isEmpty())return;
        if("messages".equals(keyName)){
            JSONObject row=new JSONObject();row.put("id",itemId);row.put("couple_id",couple);row.put("body",localItem.optString("title"));row.put("sender_id",localItem.optString("sender_id",userId()));
            request("POST","/rest/v1/couple_messages?on_conflict=id",row,true,true);
        }else{
            String kind=kindFor(keyName);if(kind==null)return;
            JSONObject payload=new JSONObject(localItem.toString());payload.remove("id");payload.put("app_key",keyName);
            if(keyName.startsWith("day_"))payload.put("date",keyName.substring(4));
            JSONObject row=new JSONObject();row.put("id",itemId);row.put("couple_id",couple);row.put("kind",kind);row.put("payload",payload);
            request("POST","/rest/v1/shared_items?on_conflict=id",row,true,true);
        }
    }

    void deleteItem(String id)throws Exception{
        String couple=coupleId();if(couple.isEmpty()||id.isEmpty())return;
        request("DELETE","/rest/v1/shared_items?id=eq."+id+"&couple_id=eq."+couple,null,true);
    }

    void deleteAccount()throws Exception{
        request("POST","/functions/v1/delete-account",new JSONObject(),true);
        prefs.edit().clear().apply();
    }
    void acknowledgeMessage(String messageId)throws Exception{JSONObject args=new JSONObject();args.put("message_id",messageId);request("POST","/rest/v1/rpc/ack_couple_message_delivery",args,true);}
    void deleteAttachment(String path)throws Exception{String safe=path.replace("%","%25").replace(" ","%20");requestValue("DELETE","/storage/v1/object/couple-files/"+safe,null,true);}

    String uploadAttachment(android.net.Uri uri,String itemId,Context context)throws Exception{
        return uploadAttachment(uri,itemId,context,null);
    }

    String uploadAttachment(android.net.Uri uri,String itemId,Context context,String contentType)throws Exception{
        String couple=coupleId();if(couple.isEmpty())throw new Exception("Сначала подключите пару");
        String type=contentType;
        if(type==null||type.isEmpty())type=context.getContentResolver().getType(uri);
        if(type==null||type.isEmpty()){
            String extension=android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.getLastPathSegment());
            type=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        if(type==null||type.isEmpty())type="application/octet-stream";
        byte[] data;
        try(InputStream in=context.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)throw new Exception("Не удалось открыть выбранное фото");
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>12*1024*1024)throw new Exception("Фото больше 12 МБ");}
            data=out.toByteArray();
        }
        String ext="jpg";int dot=type.lastIndexOf('/');if(dot>=0&&dot+1<type.length())ext=type.substring(dot+1).replaceAll("[^A-Za-z0-9]","");
        String path=couple+"/"+itemId+"-"+System.currentTimeMillis()+"."+ext;
        HttpURLConnection c=(HttpURLConnection)new URL(url()+"/storage/v1/object/couple-files/"+path).openConnection();
        c.setRequestMethod("POST");c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setDoOutput(true);
        c.setRequestProperty("apikey",key());c.setRequestProperty("Authorization","Bearer "+prefs.getString("sb_access",""));
        c.setRequestProperty("Content-Type",type);
        try(OutputStream out=c.getOutputStream()){out.write(data);}
        int status=c.getResponseCode();if(status<200||status>=300)throw new Exception("Не удалось загрузить фото (HTTP "+status+")");
        c.disconnect();return path;
    }

    byte[] downloadAttachment(String path)throws Exception{
        String safe=path.replace("\\","/");
        HttpURLConnection c=(HttpURLConnection)new URL(url()+"/storage/v1/object/authenticated/couple-files/"+safe).openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(10000);c.setReadTimeout(16000);c.setRequestProperty("apikey",key());c.setRequestProperty("Authorization","Bearer "+prefs.getString("sb_access",""));
        int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream();
        if(stream!=null){byte[] buffer=new byte[8192];int n;while((n=stream.read(buffer))!=-1)out.write(buffer,0,n);stream.close();}
        c.disconnect();if(status<200||status>=300)throw new Exception("Не удалось открыть фото (HTTP "+status+")");return out.toByteArray();
    }

    JSONArray fetchItems()throws Exception{
        String couple=coupleId();
        Object itemsObj=requestValue("GET","/rest/v1/shared_items?select=*&couple_id=eq."+couple+"&order=created_at.asc",null,true);
        Object messagesObj=requestValue("GET","/rest/v1/couple_messages?select=*&couple_id=eq."+couple+"&order=created_at.asc",null,true);
        JSONArray a=itemsObj instanceof JSONArray?(JSONArray)itemsObj:new JSONArray();
        JSONArray b=messagesObj instanceof JSONArray?(JSONArray)messagesObj:new JSONArray();
        JSONArray merged=new JSONArray();
        for(int i=0;i<a.length();i++)merged.put(a.getJSONObject(i));
        for(int i=0;i<b.length();i++)merged.put(b.getJSONObject(i));
        return merged;
    }

    void updateMessage(String messageId,String body)throws Exception{
        if(messageId==null||messageId.isEmpty())throw new Exception("Не найдено сообщение для изменения");
        if(body==null||body.trim().isEmpty()||body.length()>4000)throw new Exception("Сообщение должно содержать от 1 до 4000 символов");
        java.text.SimpleDateFormat timestamp=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",java.util.Locale.US);
        timestamp.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        JSONObject patch=new JSONObject();patch.put("body",body.trim());patch.put("edited_at",timestamp.format(new java.util.Date()));
        request("PATCH","/rest/v1/couple_messages?id=eq."+messageId+"&couple_id=eq."+coupleId()+"&sender_id=eq."+userId(),patch,true);
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

    String startWatchRoom(String title)throws Exception{
        JSONObject args=new JSONObject();args.put("target_couple_id",coupleId());args.put("target_title",title);
        try {
            Object response=requestValue("POST","/rest/v1/rpc/start_watch_room",args,true);
            String roomId=response instanceof String?(String)response:String.valueOf(response);
            roomId=roomId.replace("\"","").trim();
            if(roomId.isEmpty()||"null".equals(roomId))throw new Exception("Сервер не вернул код новой комнаты");
            return roomId;
        } catch(Exception e) {
            if(!isMissingRpc(e,"start_watch_room"))throw e;
            android.util.Log.w("VmesteWatch","start_watch_room RPC is not deployed; using REST fallback");
            JSONArray rooms=(JSONArray)requestValue("GET","/rest/v1/watch_rooms?select=id&couple_id=eq."+coupleId()+"&state=eq.active",null,true);
            for(int i=0;i<rooms.length();i++) {
                String staleId=rooms.getJSONObject(i).optString("id");
                JSONObject patch=new JSONObject();patch.put("state","closed");
                request("PATCH","/rest/v1/watch_rooms?id=eq."+staleId+"&couple_id=eq."+coupleId(),patch,true);
            }
            String roomId=UUID.randomUUID().toString();
            JSONObject row=new JSONObject();row.put("id",roomId);row.put("couple_id",coupleId());
            row.put("host_id",userId());row.put("title",title);row.put("state","active");
            request("POST","/rest/v1/watch_rooms",row,true);
            return roomId;
        }
    }

    JSONObject getWatchRoom(String roomId)throws Exception{
        JSONArray rooms=(JSONArray)requestValue("GET","/rest/v1/watch_rooms?select=id,couple_id,host_id,title,state&id=eq."+roomId+"&limit=1",null,true);
        return rooms.length()==0?null:rooms.getJSONObject(0);
    }

    JSONObject latestWatchRoom()throws Exception{
        JSONArray rows=(JSONArray)requestValue("GET","/rest/v1/watch_rooms?select=id,couple_id,host_id,title,state&couple_id=eq."+coupleId()+"&state=eq.active&order=created_at.desc&limit=1",null,true);
        return rows.length()==0?null:rows.getJSONObject(0);
    }

    void closeWatchRoom(String roomId)throws Exception{
        JSONObject args=new JSONObject();args.put("target_room_id",roomId);
        try { requestValue("POST","/rest/v1/rpc/close_watch_room",args,true); }
        catch(Exception e) {
            if(!isMissingRpc(e,"close_watch_room"))throw e;
            JSONObject patch=new JSONObject();patch.put("state","closed");
            request("PATCH","/rest/v1/watch_rooms?id=eq."+roomId+"&couple_id=eq."+coupleId(),patch,true);
        }
    }

    private boolean isMissingRpc(Exception error,String name) {
        String message=error.getMessage();
        return message!=null&&message.contains("PGRST202")&&message.contains(name);
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
            entry.put("detail","Сообщение пары");entry.put("done",false);entry.put("sender_id",row.optString("sender_id"));
            if(row.has("edited_at")&&!row.isNull("edited_at"))entry.put("edited_at",row.optString("edited_at"));
            JSONArray old=new JSONArray(target.getString("messages","[]"));
            for(int i=0;i<old.length();i++){
                JSONObject previous=old.optJSONObject(i);
                if(previous==null||!entry.optString("id").equals(previous.optString("id")))continue;
                try{
                    JSONObject remoteMedia=new JSONObject(entry.optString("title",""));
                    JSONObject localMedia=new JSONObject(previous.optString("title",""));
                    if(remoteMedia.optBoolean("vmeste_media")&&localMedia.optBoolean("vmeste_media")
                            &&!localMedia.optString("local_path").isEmpty())
                        remoteMedia.put("local_path",localMedia.optString("local_path"));
                    entry.put("title",remoteMedia.toString());
                }catch(org.json.JSONException ignored){ }
                if(previous.has("ts"))entry.put("ts",previous.optLong("ts"));
                break;
            }
            merge(target,"messages",entry);return;
        }
        JSONObject payload=row.optJSONObject("payload");if(payload==null)return;
        String key=payload.optString("app_key","");if(key.isEmpty())key=keyFor(row.optString("kind"),payload.optString("date"));if(key==null||"notes".equals(key))return;
        JSONObject entry=new JSONObject(payload.toString());entry.put("id",row.optString("id"));if(row.has("created_by"))entry.put("created_by",row.optString("created_by"));merge(target,key,entry);
    }

    private void merge(SharedPreferences target,String key,JSONObject entry)throws Exception{
        JSONArray old=new JSONArray(target.getString(key,"[]")),next=new JSONArray();String id=entry.optString("id");boolean found=false;
        for(int i=0;i<old.length();i++){JSONObject item=old.optJSONObject(i);if(item==null)continue;if(id.equals(item.optString("id"))){if(!found)next.put(entry);found=true;}else next.put(item);}
        if(!found)next.put(entry);target.edit().putString(key,next.toString()).apply();
    }

    private String kindFor(String key){
        if("list_catalog".equals(key))return "task";
        if("shared_timer".equals(key))return "event";
        if(key.startsWith("list_"))return "task";
        if("tasks".equals(key))return "task";if(key.startsWith("day_"))return "calendar_task";if("movie_room".equals(key))return "movie_room";
        if("events".equals(key))return "event";if("wishes".equals(key))return "wish";
        if("notes".equals(key)||"stories".equals(key)||"partner_card".equals(key))return "note";if("gifts".equals(key))return "gift";
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
        return requestValue(method,path,body,auth,upsert,true);
    }
    private Object requestValue(String method,String path,JSONObject body,boolean auth,boolean upsert,boolean retryAuth)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(url()+path).openConnection();c.setRequestMethod(method);c.setConnectTimeout(12000);c.setReadTimeout(16000);
        c.setRequestProperty("apikey",key());c.setRequestProperty("Accept","application/json");
        if(auth)c.setRequestProperty("Authorization","Bearer "+prefs.getString("sb_access",""));
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            if(upsert)c.setRequestProperty("Prefer","resolution=merge-duplicates,return=minimal");
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
        int status=c.getResponseCode();InputStream stream=status>=400?c.getErrorStream():c.getInputStream();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(stream!=null){byte[] buf=new byte[4096];int n;while((n=stream.read(buf))!=-1)bytes.write(buf,0,n);stream.close();}
        String response=new String(bytes.toByteArray(),StandardCharsets.UTF_8);c.disconnect();
        if(status==401&&auth&&retryAuth&&!prefs.getString("sb_refresh","").isEmpty()){refreshSession();return requestValue(method,path,body,true,upsert,false);}
        if(status<200||status>=300)throw new Exception("HTTP "+status+": "+response);
        if(response.isEmpty())return new JSONObject();char first=response.charAt(0);
        if(first=='[')return new JSONArray(response);if(first=='{')return new JSONObject(response);if(first=='\"')return response.substring(1,response.length()-1);return response;
    }
    private void refreshSession()throws Exception{
        JSONObject args=new JSONObject();args.put("refresh_token",prefs.getString("sb_refresh",""));
        JSONObject result=request("POST","/auth/v1/token?grant_type=refresh_token",args,false);
        String access=result.optString("access_token","");if(access.isEmpty())throw new Exception("Войдите в аккаунт снова");
        JSONObject user=result.optJSONObject("user");prefs.edit().putString("sb_access",access).putString("sb_refresh",result.optString("refresh_token",prefs.getString("sb_refresh",""))).putString("sb_user",user==null?userId():user.optString("id",userId())).apply();
    }
}
