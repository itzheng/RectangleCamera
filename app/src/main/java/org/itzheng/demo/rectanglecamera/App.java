package org.itzheng.demo.rectanglecamera;

import android.app.Application;
import android.content.Context;

public class App extends Application {
    public static App app;

    @Override
    public void onCreate() {
        super.onCreate();
        app = this;
    }

    public static App getInstance() {
        return app;
    }
}
