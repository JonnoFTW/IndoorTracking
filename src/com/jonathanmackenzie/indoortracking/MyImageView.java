package com.jonathanmackenzie.indoortracking;

import java.util.List;

import com.jonathanmackenzie.indoortracking.MainActivity.Point;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

public class MyImageView extends ImageView {
    private Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float scaleX, scaleY;
    private int imgSizeX, imgSizeY;
    private MainActivity ma;

    public MyImageView(Context context) {
        super(context);
        init(context);
    }

    public MyImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public MyImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
        isInEditMode();
    }

    private void init(Context context) {
        this.ma = (MainActivity) context;
        p.setStrokeWidth(0f);
        Bitmap bm = BitmapFactory.decodeResource(getResources(),
                R.drawable.ist_level_3);
        imgSizeX =774;// bm.getWidth();
        imgSizeY =863; //bm.getHeight();
        // 131px = 5m irl
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        List<Point> stepXY = ma.getSteps();
        float x = ma.getX();
        float y = ma.getY();

        // Log.i("MainActivity","Drawing imageview");
        scaleX = (float) imgSizeX / getWidth();
        scaleY = (float) imgSizeY / getHeight();
        p.setARGB(255, 0, 255, 20);
        p.setStyle(Style.FILL);

        canvas.drawCircle((x * scaleX), (y * scaleY), 10, p);
        Log.i("MyImageView",
                String.format(
                        "xy %.2f,%.2f Drawing %.2f,%.2f Scales %.2f, %.2f imgsize %d,%d canvas %d,5d",
                        x, y, (x * scaleX), (y * scaleY), scaleX, scaleY,
                        imgSizeX, imgSizeY,getWidth(),getHeight()));

        if (!stepXY.isEmpty()) {
            p.setARGB(255, 255, 20, 20);
            Point p1 = stepXY.get(0);
            for (Point point : stepXY) {
                canvas.drawLine(p1.x, p1.y, point.x, point.y, p);
                p1 = point;
            }
        }

    }
}
