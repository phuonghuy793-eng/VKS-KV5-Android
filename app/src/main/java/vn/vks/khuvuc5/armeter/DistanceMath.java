package vn.vks.khuvuc5.armeter;
import java.util.Locale;
public final class DistanceMath {
    private DistanceMath(){}
    public static String format(float meters){
        if(!Float.isFinite(meters)||meters<0)return "—";
        Locale vi=Locale.forLanguageTag("vi-VN");
        return meters<1f?String.format(vi,"≈ %.1f cm",meters*100f):String.format(vi,"≈ %.2f m",meters);
    }
}
