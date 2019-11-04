package com.kapplication.cqlive.utils;

import android.view.KeyEvent;

import com.starcor.xulapp.utils.XulLog;
import com.starcor.xulapp.utils.XulTime;

import java.util.HashMap;
import java.util.Map;

/**
 * 监听按键的序列，当用户输入某一提前注册过的序列，可以出发相应的事件
 * Created by kevin on 2017/3/22.
 */

public class KeyEventListener {

    public static final String TAG = "KeyEventListener";
    public static long lastKeyInputTimestamp = XulTime.currentTimeMillis();

    public static class KEY {


        //方向左键
        public static final char KEY_NONE = '0';

        //方向左键
        public static final char KEY_LEFT = '1';
        //方向上键
        public static final char KEY_UP = '2';
        //方向下键
        public static final char KEY_DOWN = '3';
        //方向右键
        public static final char KEY_RIGHT = '4';

        //数字键0
        public static final char KEY_NUM_0 = '5';
        //数字键1
        public static final char KEY_NUM_1 = '6';
        //数字键2
        public static final char KEY_NUM_2 = '7';
        //数字键3
        public static final char KEY_NUM_3 = '8';
        //数字键4
        public static final char KEY_NUM_4 = '9';
        //数字键5
        public static final char KEY_NUM_5 = 'A';
        //数字键6
        public static final char KEY_NUM_6 = 'B';
        //数字键7
        public static final char KEY_NUM_7 = 'C';
        //数字键8
        public static final char KEY_NUM_8 = 'D';
        //数字键9
        public static final char KEY_NUM_9 = 'E';

        //菜单按键
        public static final char KEY_MENU = 'F';

    }

    private static final StringBuilder inputSequence = new StringBuilder();
    private static final int lengthForStoreCachedSequence = 100;
    private static final Map<String, Runnable> registers = new HashMap<>();

    public static final void listenKeyInput(int keyCode) {
        lastKeyInputTimestamp = XulTime.currentTimeMillis();
        char innerCode = getInnerCode(keyCode);
        inputSequence.append(innerCode);
        int s = inputSequence.length();
        if (s > lengthForStoreCachedSequence) {
            inputSequence.deleteCharAt(0);
        }
        checkSequenceMatchState(inputSequence.toString());
    }

    private static void checkSequenceMatchState(String s) {
        synchronized (registers) {
            for (Map.Entry<String, Runnable> entry : registers.entrySet()) {
                if (!s.endsWith(entry.getKey())) {
                    continue;
                }
                XulLog.d(TAG, "matched " + entry);
                Runnable runnable = entry.getValue();
                runnable.run();
            }
        }
    }

    private static char getInnerCode(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_0:
            case KeyEvent.KEYCODE_NUMPAD_0:
                return KEY.KEY_NUM_0;

            case KeyEvent.KEYCODE_1:
            case KeyEvent.KEYCODE_NUMPAD_1:
                return KEY.KEY_NUM_1;

            case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_NUMPAD_2:
                return KEY.KEY_NUM_2;

            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_NUMPAD_3:
                return KEY.KEY_NUM_3;

            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_NUMPAD_4:
                return KEY.KEY_NUM_4;

            case KeyEvent.KEYCODE_5:
            case KeyEvent.KEYCODE_NUMPAD_5:
                return KEY.KEY_NUM_5;

            case KeyEvent.KEYCODE_6:
            case KeyEvent.KEYCODE_NUMPAD_6:
                return KEY.KEY_NUM_6;

            case KeyEvent.KEYCODE_7:
            case KeyEvent.KEYCODE_NUMPAD_7:
                return KEY.KEY_NUM_7;

            case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_NUMPAD_8:
                return KEY.KEY_NUM_8;

            case KeyEvent.KEYCODE_9:
            case KeyEvent.KEYCODE_NUMPAD_9:
                return KEY.KEY_NUM_9;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                return KEY.KEY_LEFT;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return KEY.KEY_RIGHT;

            case KeyEvent.KEYCODE_DPAD_UP:
                return KEY.KEY_UP;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                return KEY.KEY_DOWN;

            case KeyEvent.KEYCODE_MENU:
                return KEY.KEY_MENU;
        }
        return KEY.KEY_NONE;
    }


    /**
     * 为了保证尽量不影响正常的按键使用,注册的按键序列不得少于五位长度。
     * @param keys
     * @param trigger
     */
    public static void register(char[] keys, Runnable trigger) {
        if (trigger == null || keys == null || keys.length < 5) {
            XulLog.e(TAG, "注册按键序列监听失败，请检查keys的长度以及回调接口是否正确");
            return;
        }
        synchronized (registers) {
            registers.put(String.valueOf(keys), trigger);
        }
    }

    public static void unregister(char[] keys) {
        if (keys == null) {
            return;
        }
        synchronized (registers) {
            registers.remove(String.valueOf(keys));
        }
    }

    public static long lastInputTimestamp(){
        return lastKeyInputTimestamp;
    }

}
