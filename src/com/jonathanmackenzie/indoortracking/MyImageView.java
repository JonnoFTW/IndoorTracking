package com.jonathanmackenzie.indoortracking;

import java.util.List;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;

import com.jonathanmackenzie.indoortracking.MainActivity.Point;

public class MyImageView extends ImageView {
    private Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    public float scaleStep;
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
        BitmapFactory.Options dimensions = new BitmapFactory.Options(); 
        dimensions.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(getResources(), R.drawable.ist_level_3, dimensions);
        imgSizeY = dimensions.outHeight;
        imgSizeX =  dimensions.outWidth;
        Log.i("MyImageView","Image size: "+imgSizeX+", "+imgSizeY);
        //imgSizeX = 774;// bm.getWidth();
        //imgSizeY = 863; // bm.getHeight();
        // 131px = 5m irl

    }

    public float getHorizontalDistScale() {
        // pixels per meter * horizontal scale
        return (131 / 5) * getXScale();
    }
    public float getVerticalDistScale() {
        // pixels per meter * horizontal scale
        return (131 / 5) * getYScale();
    }
    public float getXScale() {
        return (float) getWidth() / imgSizeX;
    }

    public float getYScale() {
        return (float) getHeight() / imgSizeY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        List<Point> stepXY = ma.getSteps();
        p.setARGB(255, 0, 255, 20);
        p.setStyle(Style.FILL);

      /*  Log.i("MyImageView", String.format(
                // X Y is stored to scale
                "xy %.2f,%.2f Scales x:%.2f, y:%.2f, xd:%.2f, yd:%.2f view %d,%d", x, y,
                getXScale(), getYScale(), getHorizontalDistScale(), getVerticalDistScale(), getWidth(),
                getHeight()));
           */
        if (!stepXY.isEmpty()) {
            p.setARGB(255, 255, 20, 20);
            Point p1 = stepXY.get(0);
            for (Point point : stepXY) {
                canvas.drawLine(p1.x, p1.y, point.x, point.y, p);
                p1 = point;
            }
            // Draw the user 
            // should be an arrow to indicate orientation
            canvas.drawCircle((p1.x), (p1.y), 5, p);
        }

    }
}
