package com.fastkeyboard.nova;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Classic desktop-style arrow cursor: sharp hotspot at the upper-left tip. */
public class ComputerCursorView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int fillColor = Color.rgb(10, 38, 92);
    private int outlineColor = Color.rgb(2, 12, 34);

    public ComputerCursorView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        outline.setStyle(Paint.Style.STROKE);
        outline.setStrokeJoin(Paint.Join.MITER);
        outline.setStrokeCap(Paint.Cap.SQUARE);
        outline.setStrokeWidth(2.5f);
    }

    public void setCursorColors(int fillColor, int outlineColor) {
        this.fillColor = fillColor;
        this.outlineColor = outlineColor;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float sx = Math.min(w, h) / 42f;

        // Familiar Windows-style arrow silhouette. The first point is the hotspot.
        Path p = new Path();
        p.moveTo(2.0f*sx, 2.0f*sx);
        p.lineTo(2.0f*sx, 35.5f*sx);
        p.lineTo(10.0f*sx, 28.0f*sx);
        p.lineTo(15.8f*sx, 40.0f*sx);
        p.lineTo(20.0f*sx, 38.0f*sx);
        p.lineTo(14.3f*sx, 26.0f*sx);
        p.lineTo(25.0f*sx, 25.0f*sx);
        p.close();

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(fillColor);
        canvas.drawPath(p, fill);
        outline.setColor(outlineColor);
        outline.setStrokeWidth(Math.max(2f, 2.2f*sx));
        canvas.drawPath(p, outline);
    }
}
