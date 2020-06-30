package com.hollyfan.app.camremote;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.FrameLayout;

public class TestFrame extends Activity {
    private static final String TAG = "TestFrame";

    private FrameLayout preview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_frame);

        // --- For checking screen dimensions, using deprecated code
        Display display = getWindowManager().getDefaultDisplay();
        int width = display.getWidth();
        int height = display.getHeight();
        Log.d(TAG, "This phone's screen is " + Integer.toString(width) + " wide " + Integer.toString(height) + " high" );

        // --- Check screen dimensions using getRealSize()
        Point size = new Point();
        if(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.HONEYCOMB_MR2) {
            size.set(display.getWidth(), display.getHeight());
        } else
            getWindowManager().getDefaultDisplay().getRealSize(size);
        Log.d(TAG, "This phone's screen is " + size.toString());


        preview= (FrameLayout) findViewById(R.id.testFL);
        Log.d(TAG, "Setting the bitmap");
        if (CameraPreview.bm != null) {
            preview.addView(new myView(this));
            Log.d(TAG, "Bitmap is not null");

        } else{
            Log.d(TAG, "Bitmap is null");

        }

    }

    public void capture(View view) {
        Log.d(TAG, "Clicked CAPTURE button");
        //CameraActivity.capture();
        // Clear the buffers in BTChatServiceRemote so preview can restart??

    }


    private class myView extends View{

        public myView(Context context) {
            super(context);
            // TODO Auto-generated constructor stub
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // TODO Auto-generated method stub
            canvas.drawBitmap(CameraPreview.bm, 0, 0, null);
        }
    }

}
