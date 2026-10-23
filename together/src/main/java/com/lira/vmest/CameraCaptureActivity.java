package com.lira.vmest;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.Intent;
import android.util.Base64;
import java.io.File;
import java.io.FileOutputStream;

/** In-app camera UI for profile photos and round video messages. */
public final class CameraCaptureActivity extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF211D25); getWindow().setNavigationBarColor(0xFF211D25);
        web = new WebView(this); web.getSettings().setJavaScriptEnabled(true); web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.addJavascriptInterface(new Bridge(), "VmesteCamera");
        web.setWebChromeClient(new WebChromeClient() { @Override public void onPermissionRequest(PermissionRequest request) { runOnUiThread(() -> request.grant(request.getResources())); } });
        web.setWebViewClient(new WebViewClient()); setContentView(web); web.loadDataWithBaseURL("https://vmeste.local/", html(getIntent().getBooleanExtra("photo_only",false)), "text/html", "UTF-8", null);
    }
    private String html(boolean photoOnly) {
        return "<!doctype html><meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'><style>*{box-sizing:border-box}body{margin:0;background:#211d25;color:#f4edf7;font:16px sans-serif;display:flex;min-height:100vh;align-items:center;justify-content:center;flex-direction:column;gap:18px}#frame{width:min(78vw,360px);aspect-ratio:1;border-radius:50%;overflow:hidden;background:#302a35;border:3px solid #d0b3e9}video{width:100%;height:100%;object-fit:cover;transform:scaleX(-1)}button{border:0;border-radius:24px;padding:14px 20px;background:#d0b3e9;color:#241d2a;font-size:16px;font-weight:700}#stop{display:none;background:#b84f63;color:white}.row{display:flex;gap:10px}small{color:#b8acbf}</style><div id='frame'><video id='v' autoplay playsinline muted></video></div><small id='label'>Видеосообщение будет круглым</small><div class='row'><button onclick='photo()'>Снимок</button><button id='rec' onclick='record()'>● Запись</button><button id='stop' onclick='finish()'>Готово</button></div><button onclick='VmesteCamera.cancel()' style='background:#403648;color:#f4edf7'>Отмена</button><canvas id='c' hidden></canvas><script>let stream,rec,chunks=[],timer;async function start(){try{stream=await navigator.mediaDevices.getUserMedia({video:{facingMode:'user'},audio:"+(!photoOnly)+"});v.srcObject=stream}catch(e){label.textContent='Разрешите доступ к камере и микрофону'}}start();function photo(){let c=document.querySelector('#c');c.width=640;c.height=640;let x=c.getContext('2d'),w=v.videoWidth,h=v.videoHeight,s=Math.min(w,h),sx=(w-s)/2,sy=(h-s)/2;x.translate(640,0);x.scale(-1,1);x.drawImage(v,sx,sy,s,s,0,0,640,640);VmesteCamera.save(c.toDataURL('image/jpeg',.9).split(',')[1],'image/jpeg')}function record(){if(!stream)return;let type=MediaRecorder.isTypeSupported('video/webm;codecs=vp8,opus')?'video/webm;codecs=vp8,opus':'video/webm';rec=new MediaRecorder(stream,{mimeType:type});chunks=[];rec.ondataavailable=e=>{if(e.data.size)chunks.push(e.data)};rec.onstop=async()=>{let b=new Blob(chunks,{type:'video/webm'}),a=new Uint8Array(await b.arrayBuffer()),s='';for(let i=0;i<a.length;i+=0x8000)s+=String.fromCharCode(...a.subarray(i,i+0x8000));VmesteCamera.save(btoa(s),'video/webm')};rec.start();document.querySelector('#rec').style.display='none';document.querySelector('#stop').style.display='inline-block';label.textContent='Идёт запись · максимум 60 секунд';timer=setTimeout(finish,60000)}function finish(){clearTimeout(timer);if(rec&&rec.state==='recording')rec.stop()} </script>";
    }
    private final class Bridge {
        @JavascriptInterface public void save(String encoded,String mime) {
            try { String ext=mime.startsWith("image/")?".jpg":".webm"; File file=new File(getCacheDir(),"camera-"+System.currentTimeMillis()+ext); byte[] bytes=Base64.decode(encoded,Base64.DEFAULT); try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);} Intent result=new Intent();result.putExtra("path",file.getAbsolutePath());result.putExtra("mime",mime);setResult(RESULT_OK,result);finish(); }
            catch(Exception e){runOnUiThread(()->android.widget.Toast.makeText(CameraCaptureActivity.this,"Не удалось сохранить запись",android.widget.Toast.LENGTH_SHORT).show());}
        }
        @JavascriptInterface public void cancel(){runOnUiThread(()->{setResult(RESULT_CANCELED);finish();});}
    }
    @Override protected void onDestroy(){if(web!=null){web.loadUrl("about:blank");web.destroy();}super.onDestroy();}
}
