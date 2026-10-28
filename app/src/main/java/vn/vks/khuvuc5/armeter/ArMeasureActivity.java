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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final int MAX_POINTS=24;
    // ARCore anchor and Trackable access is confined to the GL thread.
    private final ArrayList<MeasurePoint> points = new ArrayList<>();
    private boolean closed = false;
    private float lastMetric = Float.NaN;
    private int movementAlertFrames = 0;
    private static final class MeasurePoint {
        final Anchor anchor;
        final Trackable trackable;
        final boolean noisy;
        MeasurePoint(Anchor a,Trackable t,boolean n){anchor=a;trackable=t;noisy=n;}
    }
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
        instruction=text("Chạm A, B, C... trên đồ vật. Có thể dùng ĐẶT ĐIỂM ở tâm ngắm.",13,Color.rgb(205,223,244));
        foot.addView(instruction);
        value=text("Chưa có điểm",27,Color.WHITE);value.setTypeface(null,1);foot.addView(value);
        LinearLayout buttons=new LinearLayout(this);
        Button set=new Button(this);set.setText("THÊM ĐIỂM");set.setAllCaps(false);set.setTextSize(12);
        Button undo=new Button(this);undo.setText("LÙI 1 ĐIỂM");undo.setAllCaps(false);undo.setTextSize(11);
        Button close=new Button(this);close.setText("KHÉP VÙNG");close.setAllCaps(false);close.setTextSize(11);
        buttons.addView(set,new LinearLayout.LayoutParams(0,px(49),2.0f));
        buttons.addView(undo,new LinearLayout.LayoutParams(0,px(49),1.3f));
        buttons.addView(close,new LinearLayout.LayoutParams(0,px(49),1.3f));
        foot.addView(buttons);
        Button clear=new Button(this);
        clear.setText("XÓA HẾT / ĐO MỚI");clear.setAllCaps(false);clear.setTextSize(11);
        foot.addView(clear,new LinearLayout.LayoutParams(-1,px(40)));
        foot.addView(text("V5: Nhiều đoạn / chu vi / diện tích. Số đo AR chỉ tham khảo.",11,Color.rgb(175,193,216)));
        root.addView(foot,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        set.setOnClickListener(v->{
            pendingTap.set(new PointF(width/2f,height/2f));
            inform("Đang chọn điểm trên tâm ngắm...");
        });
        undo.setOnClickListener(v->display.queueEvent(()->{
            pendingTap.set(null);
            if(!points.isEmpty()){
                MeasurePoint p=points.remove(points.size()-1);
                p.anchor.detach();
            }
            closed=false;lastMetric=Float.NaN;movementAlertFrames=0;
            runOnUiThread(()->{
                instruction.setText("Đã hoàn tác. Chạm để đặt tiếp.");
                value.setText(points.size()+" điểm");
            });
        }));
        close.setOnClickListener(v->display.queueEvent(()->{
            if(points.size()<3){
                inform("Cần ít nhất 3 điểm để khép vùng và tính diện tích.");
                return;
            }
            for(MeasurePoint p:points){
                if(p.anchor.getTrackingState()!=TrackingState.TRACKING || provisional(p.trackable)){
                    inform("Có điểm đang mất định vị / tạm. Chưa thể khép vùng.");
                    return;
                }
            }
            closed=true;lastMetric=Float.NaN;
            inform("Đã khép vùng. Xem chu vi và diện tích nếu các điểm cùng mặt.");
        }));
        clear.setOnClickListener(v->display.queueEvent(()->{
            pendingTap.set(null);
            for(MeasurePoint p:points)p.anchor.detach();
            points.clear();closed=false;lastMetric=Float.NaN;movementAlertFrames=0;
            runOnUiThread(()->{
                value.setText("Chưa có điểm");
                instruction.setText("Chạm A, B, C... trên đồ vật. Có thể dùng ĐẶT ĐIỂM.");
                layer.setPoints(new ArrayList<>(),new float[0],false);
            });
            inform("Đã xóa các điểm, sẵn sàng đo mới.");
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
            inform("Di chuyển camera nhẹ để AR nhận diện hình dạng vật thể.");
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
                inform("Camera đang mất định vị. Hướng vào vùng có họa tiết và di chuyển chậm.");
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
        // A detected plane is more stable than noisy scene depth when they agree spatially.
        if(depth!=null && plane!=null &&
           distance(depth.getHitPose(),plane.getHitPose())<0.055f) return plane;
        return depth!=null?depth:(plane!=null?plane:feature);
    }
    private boolean provisional(Trackable t){
        return t instanceof InstantPlacementPoint
            &&((InstantPlacementPoint)t).getTrackingMethod()!=InstantPlacementPoint.TrackingMethod.FULL_TRACKING;
    }
    /** Neighbouring AR depth hits are compared to flag abrupt discontinuities/edges. */
    private boolean noisyDepth(Frame frame,PointF tap,HitResult center){
        if(!(center.getTrackable() instanceof DepthPoint))return false;
        final float[][] offsets={{-9,0},{9,0},{0,-9},{0,9}};
        int matches=0, disagrees=0;
        for(float[] o:offsets){
            for(HitResult h:frame.hitTest(tap.x+o[0],tap.y+o[1])){
                if(h.getTrackable() instanceof DepthPoint &&
                   h.getTrackable().getTrackingState()==TrackingState.TRACKING){
                    matches++;
                    float error=distance(center.getHitPose(),h.getHitPose());
                    float allowed=Math.max(0.07f,0.10f*center.getDistance());
                    if(error>allowed)disagrees++;
                    break;
                }
            }
        }
        return matches<2 || disagrees>=2;
    }
    private void place(Frame frame,PointF location){
        if(closed){
            inform("Vùng đã khép. Chọn XÓA HẾT để đo mới hoặc LÙI 1 ĐIỂM để sửa.");
            return;
        }
        if(points.size()>=MAX_POINTS){
            inform("Đã đủ 24 điểm. Dùng KHÉP VÙNG hoặc XÓA HẾT.");
            return;
        }
        HitResult h=hit(frame,location.x,location.y);
        boolean estimate=false;
        if(h==null){
            for(HitResult candidate:frame.hitTestInstantPlacement(location.x,location.y,1.0f)){
                if(candidate.getTrackable() instanceof InstantPlacementPoint){
                    h=candidate;estimate=true;break;
                }
            }
        }
        if(h==null){
            inform("Chưa bắt được điểm AR; thử di chuyển nhẹ hoặc chọn chỗ có chi tiết.");
            return;
        }
        boolean noisy=noisyDepth(frame,location,h);
        Trackable trackable=h.getTrackable();
        Anchor anchor=h.createAnchor();
        points.add(new MeasurePoint(anchor,trackable,noisy));
        int n=points.size();
        lastMetric=Float.NaN;movementAlertFrames=0;
        String label=String.valueOf((char)('A'+n-1));
        if(noisy) inform("Điểm "+label+" nằm gần vùng đổi độ sâu; số đo có thể lệch. Dùng LÙI 1 ĐIỂM nếu cần.");
        else if(estimate||provisional(trackable))
            inform("Đã đặt điểm "+label+" tạm: chờ AR xác định độ sâu thực.");
        else inform("Đã đặt điểm "+label+". Chạm để thêm điểm tiếp theo.");
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
    private static float pointArea3D(List<Pose> poses){
        // Newell area vector. Valid for a simple near-planar 3D polygon only.
        Pose base=poses.get(0);
        float nx=0,ny=0,nz=0;
        for(int i=0;i<poses.size();i++){
            Pose u=poses.get(i),v=poses.get((i+1)%poses.size());
            float ux=u.tx()-base.tx(),uy=u.ty()-base.ty(),uz=u.tz()-base.tz();
            float vx=v.tx()-base.tx(),vy=v.ty()-base.ty(),vz=v.tz()-base.tz();
            nx+=uy*vz-uz*vy;
            ny+=uz*vx-ux*vz;
            nz+=ux*vy-uy*vx;
        }
        float mag=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
        if(mag<1e-5f)return Float.NaN;
        nx/=mag;ny/=mag;nz/=mag;
        float largest=0;
        for(Pose p:poses)largest=Math.max(largest,distance(base,p));
        float threshold=Math.max(0.035f,largest*.035f);
        for(Pose p:poses){
            float deviation=Math.abs(nx*(p.tx()-base.tx())+
                ny*(p.ty()-base.ty())+nz*(p.tz()-base.tz()));
            if(deviation>threshold)return Float.NaN;
        }
        return mag*.5f;
    }
    private static String areaText(float sqMeters){
        if(!Float.isFinite(sqMeters)||sqMeters<0)return "Chưa xác định";
        Locale vi=Locale.forLanguageTag("vi-VN");
        if(sqMeters<0.1f)return String.format(vi,"≈ %.0f cm²",sqMeters*10000f);
        return String.format(vi,"≈ %.2f m²",sqMeters);
    }
    private void renderAnchors(com.google.ar.core.Camera camera){
        final int n=points.size();
        ArrayList<PointF> screenPoints=new ArrayList<>(n);
        ArrayList<Pose> poses=new ArrayList<>(n);
        boolean complete=true,uncertain=false;
        for(MeasurePoint point:points){
            screenPoints.add(position(point.anchor,camera));
            if(point.anchor.getTrackingState()!=TrackingState.TRACKING ||
               provisional(point.trackable)){
                complete=false;
            }
            if(point.noisy)uncertain=true;
            poses.add(point.anchor.getPose());
        }
        final float[] lengths=new float[Math.max(0,n-1)];
        float total=0;
        for(int i=1;i<n;i++){
            float length=distance(poses.get(i-1),poses.get(i));
            lengths[i-1]=length;total+=length;
        }
        if(closed && n>=3)total+=distance(poses.get(n-1),poses.get(0));
        float area=closed && n>=3 && complete?pointArea3D(poses):Float.NaN;
        if(complete && n>=2){
            if(Float.isFinite(lastMetric)){
                float difference=Math.abs(lastMetric-total);
                if(difference>Math.max(0.018f,lastMetric*.035f))
                    movementAlertFrames=25;
            }
            lastMetric=total;
        }else lastMetric=Float.NaN;
        if(movementAlertFrames>0)movementAlertFrames--;
        final boolean unstable=movementAlertFrames>0;
        final boolean completeNow=complete;
        final boolean uncertainNow=uncertain;
        final float measured=total;
        final float areaNow=area;
        final boolean isClosed=closed;
        runOnUiThread(()->{
            layer.setPoints(screenPoints,lengths,isClosed);
            String caption;
            if(n==0)caption="Chưa có điểm";
            else if(n==1)caption="Đã đặt A • thêm điểm B";
            else if(!completeNow)caption="Đang xác định độ sâu...";
            else caption=(isClosed?"Chu vi ":"Tổng ")+DistanceMath.format(measured);
            value.setText(caption);
            if(n>1 && isClosed && completeNow){
                instruction.setText("Diện tích "+areaText(areaNow)+
                    (uncertainNow||unstable?" • ⚠ Kiểm tra lại sai số":" • Kết quả AR tham khảo"));
            }else if(n>1){
                instruction.setText(n+" điểm • "+(uncertainNow||unstable?
                    "⚠ Độ sâu thay đổi, nên đo lại điểm nghi ngờ":
                    !completeNow?"Có mốc tạm / mất định vị":
                    "Thêm điểm hoặc khép vùng (từ 3 điểm)"));
            }else instruction.setText("Đã đặt "+n+" điểm. Chạm để thêm điểm kế tiếp.");
        });
    }
    /** Lightweight HUD: marks A-X, segment lengths and optional closing edge. */
    private class MeasureLayer extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private List<PointF> current=new ArrayList<>();
        private float[] lengths=new float[0];
        private boolean closedLine=false,ready=false;
        MeasureLayer(){super(ArMeasureActivity.this);}
        void setReady(boolean ready){if(this.ready!=ready){this.ready=ready;invalidate();}}
        void setPoints(List<PointF> p,float[] segments,boolean isClosed){
            current=p;lengths=segments;closedLine=isClosed;invalidate();
        }
        private void drawEdge(Canvas canvas,PointF u,PointF v,boolean closing){
            if(u==null||v==null)return;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(px(6));paint.setColor(Color.BLACK);
            canvas.drawLine(u.x,u.y,v.x,v.y,paint);
            paint.setStrokeWidth(px(3));paint.setColor(closing?Color.CYAN:Color.YELLOW);
            canvas.drawLine(u.x,u.y,v.x,v.y,paint);
            paint.setStyle(Paint.Style.FILL);
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);
            float cx=getWidth()/2f,cy=getHeight()/2f;
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(px(3));
            paint.setColor(ready?Color.rgb(25,213,150):Color.rgb(255,194,50));
            c.drawCircle(cx,cy,px(17),paint);
            c.drawLine(cx-px(8),cy,cx+px(8),cy,paint);
            c.drawLine(cx,cy-px(8),cx,cy+px(8),paint);
            for(int i=1;i<current.size();i++){
                PointF a=current.get(i-1),b=current.get(i);
                drawEdge(c,a,b,false);
                if(a!=null&&b!=null&&i-1<lengths.length){
                    paint.setTextSize(px(12));paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    String txt=DistanceMath.format(lengths[i-1]);
                    float xx=(a.x+b.x)/2f,yy=(a.y+b.y)/2f-px(7);
                    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(px(3));
                    paint.setColor(Color.BLACK);c.drawText(txt,xx,yy,paint);
                    paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
                    c.drawText(txt,xx,yy,paint);
                }
            }
            if(closedLine&&current.size()>=3)
                drawEdge(c,current.get(current.size()-1),current.get(0),true);
            for(int i=0;i<current.size();i++){
                PointF p=current.get(i);if(p==null)continue;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.rgb(237,71,54));
                c.drawCircle(p.x,p.y,px(6),paint);
                paint.setColor(Color.WHITE);
                paint.setTextSize(px(15));paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(px(3));
                paint.setColor(Color.BLACK);c.drawText(""+(char)('A'+i),p.x+px(8),p.y-px(8),paint);
                paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);
                c.drawText(""+(char)('A'+i),p.x+px(8),p.y-px(8),paint);
            }
        }
    }

}

