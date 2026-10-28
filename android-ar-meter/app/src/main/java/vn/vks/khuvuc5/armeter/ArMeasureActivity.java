package vn.vks.khuvuc5.armeter;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.Coordinates2d;
import com.google.ar.core.DepthPoint;
import com.google.ar.core.InstantPlacementPoint;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** Android ARCore native. No HTML, WebView, photo-scale guesswork or old APK assets. */
public class ArMeasureActivity extends Activity implements GLSurfaceView.Renderer {
    private static final int CAMERA_REQUEST = 41;
    private static final String VS = "attribute vec2 aPosition; attribute vec2 aTexCoord; varying vec2 vTexCoord; void main(){gl_Position=vec4(aPosition,0.,1.); vTexCoord=aTexCoord;}";
    private static final String FS = "#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 vTexCoord; uniform samplerExternalOES uTexture; void main(){gl_FragColor=texture2D(uTexture,vTexCoord);}";
    private final FloatBuffer positions = floats(new float[]{-1,-1,1,-1,-1,1,1,1});
    private final FloatBuffer coords = floats(new float[8]);
    private final AtomicReference<PointF> pendingTap = new AtomicReference<>();
    private GLSurfaceView display;
    private MeasureLayer layer;
    private TextView status, value, instruction;
    private volatile Session session;
    private boolean requestedInstall, displayRunning;
    private int texture=-1, program;
    private volatile int width=1,height=1;
    private Anchor anchorA, anchorB;
    // Track each independently: depth, plane, feature point or provisional instant placement.
    private Trackable trackableA, trackableB;
    private int frames=0;
    private boolean hitReady=false;
    private String statusText="";

    private static FloatBuffer floats(float[] arr) {
        FloatBuffer b=ByteBuffer.allocateDirect(arr.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(arr);b.position(0);return b;
    }
    private int px(float dp){return (int)(getResources().getDisplayMetrics().density*dp+0.5f);}
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);return t;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root=new FrameLayout(this); root.setBackgroundColor(Color.BLACK);
        display=new GLSurfaceView(this);
        display.setEGLContextClientVersion(2);
        display.setPreserveEGLContextOnPause(true);
        display.setRenderer(this);
        display.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        root.addView(display,new FrameLayout.LayoutParams(-1,-1));
        display.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_UP){pendingTap.set(new PointF(e.getX(),e.getY()));inform("Đã chạm camera — đang chọn tọa độ 3D...");}
            return true;
        });
        layer=new MeasureLayer();
        layer.setClickable(false);
        root.addView(layer,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(px(15),px(10),px(15),px(10));
        head.setBackgroundColor(Color.argb(220,8,20,39));
        TextView heading=text("THƯỚC ĐO AR • VKS",17,Color.WHITE);heading.setTypeface(null,1);head.addView(heading);
        status=text("Đang mở camera AR...",13,Color.rgb(200,220,243));head.addView(status);
        root.addView(head,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));

        LinearLayout foot=new LinearLayout(this);foot.setOrientation(LinearLayout.VERTICAL);
        foot.setPadding(px(15),px(10),px(15),px(15));
        foot.setBackgroundColor(Color.argb(230,8,20,39));
        instruction=text("Chạm A rồi B trên vật cần đo. Được chọn hai độ cao khác nhau.",13,Color.rgb(205,223,244));
        foot.addView(instruction);
        value=text("—",35,Color.WHITE);value.setTypeface(null,1);foot.addView(value);
        LinearLayout buttons=new LinearLayout(this);
        Button set=new Button(this);set.setText("ĐẶT ĐIỂM");set.setAllCaps(false);
        Button clear=new Button(this);clear.setText("ĐO LẠI");clear.setAllCaps(false);
        buttons.addView(set,new LinearLayout.LayoutParams(0,px(54),2));
        buttons.addView(clear,new LinearLayout.LayoutParams(0,px(54),1));
        foot.addView(buttons);
        foot.addView(text("V4: Đo đồ vật, mặt đường, tường và nhiều bề mặt. Mốc tạm CHƯA có số đo tin cậy.",11,Color.rgb(175,193,216)));
        root.addView(foot,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        set.setOnClickListener(v->{pendingTap.set(new PointF(width/2f,height/2f));inform("Đã bấm đặt điểm — đang tìm tọa độ 3D...");});
        clear.setOnClickListener(v->display.queueEvent(()->{
            if(anchorA!=null){anchorA.detach();anchorA=null;}
            if(anchorB!=null){anchorB.detach();anchorB=null;}
            trackableA=null;trackableB=null;
            runOnUiThread(()->{
                value.setText("—");
                instruction.setText("Chạm A rồi B trên hai vị trí bất kỳ của vật.");
                layer.setAnchors(null,null);
            });
        }));
        setContentView(root);
    }
    @Override protected void onResume(){
        super.onResume();
        if(session==null){
            try {
                ArCoreApk.InstallStatus install=ArCoreApk.getInstance().requestInstall(this,!requestedInstall);
                if(install==ArCoreApk.InstallStatus.INSTALL_REQUESTED){
                    requestedInstall=true;
                    inform("Hãy cài/cập nhật Google Play Services for AR, sau đó quay lại.");
                    return;
                }
                if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
                    requestPermissions(new String[]{Manifest.permission.CAMERA},CAMERA_REQUEST);
                    return;
                }
                Session s=new Session(this);
                Config cfg=new Config(s);
                cfg.setPlaneFindingMode(Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL);
                // Support non-planar objects where the device has depth sensing.
                if(s.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
                    cfg.setDepthMode(Config.DepthMode.AUTOMATIC);
                else cfg.setDepthMode(Config.DepthMode.DISABLED);
                // Visual fallback marker: shown immediately but NOT counted as valid metric depth.
                cfg.setInstantPlacementMode(Config.InstantPlacementMode.LOCAL_Y_UP);
                s.configure(cfg);session=s;
            }catch(UnavailableException|SecurityException ex){
                inform("ARCore chưa sẵn sàng: "+ex.getClass().getSimpleName());return;
            }
        }
        try{
            session.resume();
            display.onResume();displayRunning=true;
            inform("Di chuyển camera chậm để tìm nền/tường có hoa văn.");
        }catch(CameraNotAvailableException ex){inform("Không truy cập được camera. Đóng ứng dụng dùng camera khác.");}
        catch(Exception ex){inform("Không khởi động AR: "+ex.getClass().getSimpleName());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){
        super.onRequestPermissionsResult(request,permissions,grants);
        if(request==CAMERA_REQUEST){
            if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)onResume();
            else inform("Hãy cấp quyền Camera trong Cài đặt ứng dụng.");
        }
    }
    @Override protected void onPause(){
        if(displayRunning){display.onPause();displayRunning=false;}
        if(session!=null)session.pause();
        super.onPause();
    }
    @Override protected void onDestroy(){
        if(session!=null){session.close();session=null;}
        super.onDestroy();
    }
    private void inform(String msg){
        if(msg.equals(statusText))return;
        statusText=msg;
        runOnUiThread(()->{if(status!=null)status.setText(msg);});
    }
    private int shader(int type,String source){
        int id=GLES20.glCreateShader(type);
        GLES20.glShaderSource(id,source);GLES20.glCompileShader(id);
        return id;
    }
    @Override public void onSurfaceCreated(GL10 unused,EGLConfig cfg){
        GLES20.glClearColor(0,0,0,1);
        int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);texture=ids[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        program=GLES20.glCreateProgram();
        int vs=shader(GLES20.GL_VERTEX_SHADER,VS),fs=shader(GLES20.GL_FRAGMENT_SHADER,FS);
        GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);
        GLES20.glLinkProgram(program);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
    }
    @Override public void onSurfaceChanged(GL10 gl,int w,int h){
        width=Math.max(1,w);height=Math.max(1,h);GLES20.glViewport(0,0,width,height);
    }
    @Override public void onDrawFrame(GL10 unused){
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        Session s=session;
        if(s==null||texture<0)return;
        try{
            s.setCameraTextureName(texture);
            s.setDisplayGeometry(getWindowManager().getDefaultDisplay().getRotation(),width,height);
            Frame f=s.update();
            drawCamera(f);
            com.google.ar.core.Camera cam=f.getCamera();
            if(cam.getTrackingState()!=TrackingState.TRACKING){
                inform("Đang định vị không gian... Hướng vào mặt sàn có hoa văn.");
                pendingTap.set(null);
                if(frames++%12==0)runOnUiThread(()->layer.setReady(false));
                return;
            }
            PointF tap=pendingTap.getAndSet(null);
            if(tap!=null)place(f,tap);
            if(frames++%7==0){
                boolean ready=hit(f,width/2f,height/2f)!=null;
                if(ready!=hitReady){
                    hitReady=ready;runOnUiThread(()->layer.setReady(ready));
                    inform(ready?"Đã có điểm 3D: bấm ĐẶT ĐIỂM hoặc chạm vật.":"Có thể chấm điểm tạm; di chuyển nhẹ để AR lấy độ sâu.");
                }
            }
            if(frames%3==0)renderAnchors(cam);
        }catch(CameraNotAvailableException ex){inform("Camera không khả dụng.");}
        catch(Exception ex){inform("Lỗi AR: "+ex.getClass().getSimpleName());}
    }
    private void drawCamera(Frame f){
        positions.rewind();coords.rewind();
        f.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,positions,
            Coordinates2d.TEXTURE_NORMALIZED,coords);
        positions.rewind();coords.rewind();
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"uTexture"),0);
        int v=GLES20.glGetAttribLocation(program,"aPosition");
        int u=GLES20.glGetAttribLocation(program,"aTexCoord");
        GLES20.glEnableVertexAttribArray(v);GLES20.glEnableVertexAttribArray(u);
        GLES20.glVertexAttribPointer(v,2,GLES20.GL_FLOAT,false,0,positions);
        GLES20.glVertexAttribPointer(u,2,GLES20.GL_FLOAT,false,0,coords);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(v);GLES20.glDisableVertexAttribArray(u);
    }
    // Prefer scene-depth (arbitrary shapes), then detected planes, then visual feature points.
    // Never require both A and B to be on the same plane: height and 3D gaps are valid.
    private HitResult hit(Frame frame,float x,float y){
        HitResult depth=null,plane=null,feature=null;
        for(HitResult h:frame.hitTest(x,y)){
            Trackable t=h.getTrackable();
            if(t.getTrackingState()!=TrackingState.TRACKING)continue;
            float meters=h.getDistance();
            if(!Float.isFinite(meters)||meters<0.10f||meters>20f)continue;
            if(t instanceof DepthPoint){
                if(depth==null)depth=h;
            }else if(t instanceof Plane){
                Plane p=(Plane)t;
                if(plane==null&&p.isPoseInPolygon(h.getHitPose()))plane=h;
            }else if(t instanceof Point){
                if(feature==null)feature=h;
            }
        }
        return depth!=null?depth:(plane!=null?plane:feature);
    }
    private boolean provisional(Trackable t){
        return t instanceof InstantPlacementPoint
            &&((InstantPlacementPoint)t).getTrackingMethod()!=InstantPlacementPoint.TrackingMethod.FULL_TRACKING;
    }
    private void place(Frame frame,PointF location){
        HitResult h=hit(frame,location.x,location.y);
        boolean estimated=false;
        // Never silently invent a measured length when the AR scene has no true 3D hit.
        if(h==null){
            for(HitResult candidate:frame.hitTestInstantPlacement(location.x,location.y,1.0f)){
                if(candidate.getTrackable() instanceof InstantPlacementPoint){
                    h=candidate;estimated=true;break;
                }
            }
        }
        if(h==null){inform("Chưa đặt được mốc: camera đang mất định vị. Thử hướng sang vùng có chi tiết.");return;}
        if(anchorA!=null&&anchorB!=null){
            anchorA.detach();anchorB.detach();
            anchorA=null;anchorB=null;trackableA=null;trackableB=null;
        }
        Trackable chosen=h.getTrackable();
        Anchor fresh=h.createAnchor();
        final boolean initialEstimate=estimated||provisional(chosen);
        if(anchorA==null){
            anchorA=fresh;trackableA=chosen;
            runOnUiThread(()->{
                value.setText(initialEstimate?"A tạm":"A ✓");
                instruction.setText("Đã chọn A. Chạm B ở bất kỳ độ cao hoặc vật khác.");
            });
        }else{
            anchorB=fresh;trackableB=chosen;
            runOnUiThread(()->instruction.setText("Đã chọn A–B. Chạm tiếp để đo đoạn khác."));
        }
        String source=chosen instanceof DepthPoint?"chiều sâu":
            chosen instanceof Plane?"mặt phẳng":
            chosen instanceof Point?"điểm đặc trưng":"vị trí tạm";
        inform(initialEstimate?
            "Đã đặt mốc TẠM; đang đợi AR xác định khoảng cách thực.":
            "Đã đặt điểm 3D bằng "+source+".");
    }
    private static float distance(Pose a,Pose b){
        float dx=a.tx()-b.tx(),dy=a.ty()-b.ty(),dz=a.tz()-b.tz();
        return (float)Math.sqrt(dx*dx+dy*dy+dz*dz);
    }
    private PointF position(Anchor a,com.google.ar.core.Camera camera){
        if(a==null||a.getTrackingState()!=TrackingState.TRACKING)return null;
        float[] proj=new float[16],view=new float[16],vp=new float[16],clip=new float[4];
        camera.getProjectionMatrix(proj,0,.05f,100f);
        camera.getViewMatrix(view,0);
        Matrix.multiplyMM(vp,0,proj,0,view,0);
        Pose p=a.getPose();
        Matrix.multiplyMV(clip,0,vp,0,new float[]{p.tx(),p.ty(),p.tz(),1},0);
        if(clip[3]<=0)return null;
        return new PointF((clip[0]/clip[3]+1)*.5f*width,(1-clip[1]/clip[3])*.5f*height);
    }
    private void renderAnchors(com.google.ar.core.Camera camera){
        PointF a=position(anchorA,camera),b=position(anchorB,camera);
        final boolean incomplete=provisional(trackableA)||provisional(trackableB);
        final boolean firstEstimated=provisional(trackableA);
        final Float len=(anchorA!=null&&anchorB!=null&&
            anchorA.getTrackingState()==TrackingState.TRACKING&&
            anchorB.getTrackingState()==TrackingState.TRACKING)?
            distance(anchorA.getPose(),anchorB.getPose()):null;
        runOnUiThread(()->{
            layer.setAnchors(a,b);
            if(len!=null)value.setText(incomplete?"Đang xác định độ sâu...":DistanceMath.format(len));
            else if(anchorA!=null&&anchorB==null)value.setText(firstEstimated?"A tạm":"A ✓");
        });
    }
    /** Project persistent 3D anchors to a lightweight Canvas overlay. */
    private class MeasureLayer extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private PointF a,b;
        private boolean ready=false;
        MeasureLayer(){super(ArMeasureActivity.this);}
        void setReady(boolean ready){if(this.ready!=ready){this.ready=ready;invalidate();}}
        void setAnchors(PointF a,PointF b){this.a=a;this.b=b;invalidate();}
        private void mark(Canvas c,PointF p){
            if(p==null)return;
            paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(237,71,54));
            Path flag=new Path();
            float x=p.x,y=p.y;
            flag.moveTo(x,y-42);flag.lineTo(x+24,y-34);flag.lineTo(x,y-23);flag.close();
            c.drawPath(flag,paint);
            paint.setColor(Color.WHITE);paint.setStrokeWidth(3);
            c.drawLine(x,y,x,y-42,paint);
            c.drawCircle(x,y,6,paint);
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float cx=getWidth()/2f,cy=getHeight()/2f,dp=getResources().getDisplayMetrics().density;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3*dp);
            paint.setColor(ready?Color.rgb(25,213,150):Color.rgb(255,194,50));
            c.drawCircle(cx,cy,17*dp,paint);
            c.drawLine(cx-8*dp,cy,cx+8*dp,cy,paint);
            c.drawLine(cx,cy-8*dp,cx,cy+8*dp,paint);
            paint.setStyle(Paint.Style.FILL);
            if(a!=null&&b!=null){
                paint.setStrokeWidth(7*dp);paint.setColor(Color.BLACK);c.drawLine(a.x,a.y,b.x,b.y,paint);
                paint.setStrokeWidth(4*dp);paint.setColor(Color.YELLOW);c.drawLine(a.x,a.y,b.x,b.y,paint);
            }
            mark(c,a);mark(c,b);
        }
    }
}
