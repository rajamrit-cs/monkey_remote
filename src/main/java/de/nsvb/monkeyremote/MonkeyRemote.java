/*
 * Copyright (C) 2016-2020 ns130291
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.nsvb.monkeyremote;

import com.android.chimpchat.ChimpChat;
import com.android.chimpchat.core.IChimpDevice;
import com.android.chimpchat.core.TouchPressType;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
// import java.awt.event.KeyAdapter;
// import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.WindowConstants;
import javax.swing.event.MouseInputAdapter;

/**
 *
 * @author ns130291
 */
public class MonkeyRemote extends JFrame {

    private static final String ADB = "/usr/bin/adb";
    private static final long TIMEOUT = 5000;
    // private static String phone_name = "";
    private static float scalingFactor = 0.3f;

    private final IChimpDevice device;

    public MonkeyRemote(IChimpDevice device, int deviceWidth, int deviceHeight, BufferedImage initialScreen, String phone_name) {
        this.device = device;

        int dWScaled = (int) (deviceWidth * scalingFactor);
        int dHScaled = (int) (deviceHeight * scalingFactor);

        setTitle(phone_name);

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        addWindowListener(new WindowAdapter() {

            @Override
            public void windowClosing(WindowEvent e) {
                MonkeyRemote.this.device.dispose();
            }

        });

        DeviceScreen screen = new DeviceScreen(initialScreen, dWScaled, dHScaled);

        GestureListener gestureListener = new GestureListener(device);
        screen.addMouseListener(gestureListener);
        screen.addMouseMotionListener(gestureListener);

        add(screen);
        pack();
        setVisible(true);

        int i = 1;
        int error_counter = 0;
        while (true) {
            if (error_counter > 10) {
                break;
            }
            // System.out.println("#" + i++);
            try {
                BufferedImage screenImage = device.takeSnapshot().getBufferedImage();
                if (screenImage.getWidth() != deviceWidth || screenImage.getHeight() != deviceHeight) {
                    deviceWidth = screenImage.getWidth();
                    deviceHeight = screenImage.getHeight();
                    dWScaled = (int) (deviceWidth * scalingFactor);
                    dHScaled = (int) (deviceHeight * scalingFactor);
                    screen.updateScreenSize(dWScaled, dHScaled);
                    screen.setPreferredSize(new Dimension(dWScaled, dHScaled));
                    pack();
                }
                screen.setImage(screenImage);
                screen.repaint();
                if (error_counter > 0) {
                    error_counter--;
                }
            } catch (Exception ex) {
                error_counter++;
                System.out.println("Couldn't aquire screenshot: " + ex.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        String adb = ADB;
        String phone_name;
        if (args.length == 3 || !new File(adb).exists() || new File(adb).isDirectory()) {
            if (!new File(adb).exists()) {
                System.err.println("Error: ADB executable wasn't found at \"" + adb + "\"");
            }
            if (new File(adb).isDirectory()) {
                System.err.println("Error: Path to ADB executable is a directory");
            }
        }else{
            System.out.println("Usage: MonkeyRemote [Path to ADB executable] [Scaling factor] [Device Serial Number]");
            return;
        }

        adb = args[0];
        scalingFactor = Float.parseFloat(args[1]);
        phone_name = args[2];
        if(phone_name.equals("")){
            System.out.println("Error: Device name cann't be empty");
            System.exit(0);
        }


        ArrayList<String> phone_list = new ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec("adb devices");
//            System.out.println(p.pid());
//            System.out.println(p.exitValue());
            p.getOutputStream().close();
            BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            input.readLine();
            String line;
            while ((line = input.readLine()) != null) {
                String temp = line.split("\t")[0];
                if (!temp.isEmpty()) {
                    phone_list.add(temp);
                }
            }
//            System.out.println(phone_list);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (phone_name.trim().equals("")) {
            if(phone_list.isEmpty()){
                System.out.println("Device not connected.");
                System.exit(0);
            }
            phone_name = phone_list.get(0);
        }
        //http://stackoverflow.com/questions/6686085/how-can-i-make-a-java-app-using-the-monkeyrunner-api
        Map<String, String> options = new TreeMap<>();
        options.put("backend", "adb");
        options.put("adbLocation", adb);
        ChimpChat chimpchat = ChimpChat.getInstance(options);
        IChimpDevice device = chimpchat.waitForConnection(TIMEOUT, phone_name);

        if (device == null) {
            System.err.println("Error: Couldn't connect to device with serial number " + phone_name);
            System.exit(0);
            return;
        }
        String phone_model = "";
        for (String prop : device.getPropertyList()) {
            if(prop.equals("build.model") || prop.equals("build.manufacturer")){
                if(phone_model.equals("")){
                    phone_model += device.getProperty(prop);
                }else{
                    phone_model += "(" + device.getProperty(prop) + ")";
                }
                
            }
        //  System.out.println(prop + ": " + device.getProperty(prop));
         }
        device.wake();
        BufferedImage screen = device.takeSnapshot().getBufferedImage();

        int width = screen.getWidth();
        int height = screen.getHeight();

        // System.out.println("Device screen dimension:" + height + "x" + width);

        MonkeyRemote remote = new MonkeyRemote(device, width, height, screen, phone_model);
        //chimpchat.shutdown();
    }

    private class DeviceScreen extends JPanel {

        private BufferedImage image;
        private int dWScaled;
        private int dHScaled;

        public DeviceScreen(BufferedImage image, int dWScaled, int dHScaled) {
            this.image = image;
            this.dWScaled = dWScaled;
            this.dHScaled = dHScaled;
            setPreferredSize(new Dimension(dWScaled, dHScaled));
        }

        public void setImage(BufferedImage image) {
            this.image = image;
        }
        
        public void updateScreenSize(int dWScaled, int dHScaled) {
            this.dWScaled = dWScaled;
            this.dHScaled = dHScaled;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.drawImage(image, 0, 0, dWScaled, dHScaled, null);
        }
    }

    private class GestureListener extends MouseInputAdapter {

        private boolean gestureActive = false;
        private final IChimpDevice device;
        private long lastSent = 0;
        private int lastX = 0;
        private int lastY = 0;

        public GestureListener(IChimpDevice device) {
            this.device = device;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                if (gestureActive) {
                    sendTouchEvent(lastX, lastY, TouchPressType.UP);
                    System.out.println("UP, cancelling old gesture " + lastX + " " + lastY);
                }
                int x = (int) (e.getX() / scalingFactor);
                int y = (int) (e.getY() / scalingFactor);
                gestureActive = true;
                lastX = x;
                lastY = y;
                sendTouchEvent(x, y, TouchPressType.DOWN);
                System.out.println("DOWN " + x + " " + y);
            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                if (gestureActive) {
                    int x = (int) (e.getX() / scalingFactor);
                    int y = (int) (e.getY() / scalingFactor);
                    sendTouchEvent(x, y, TouchPressType.UP);
                    gestureActive = false;
                    System.out.println("UP " + x + " " + y);
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (gestureActive) {
                //System.out.println("mouse dragged " + (int) (e.getX() / scalingFactor) + " " + (int) (e.getY() / scalingFactor) + " " + e.getButton());
                if (System.currentTimeMillis() - lastSent > 1) { // max every 2 milliseconds
                    int x = (int) (e.getX() / scalingFactor);
                    int y = (int) (e.getY() / scalingFactor);
                    sendTouchEvent(x, y, TouchPressType.MOVE);
                    lastX = x;
                    lastY = y;
                    lastSent = System.currentTimeMillis();
                    System.out.println("MOVE " + x + " " + y);
                }
            }
        }

        private void sendTouchEvent(int x, int y, TouchPressType type) {
            device.touch(x, y, type);
        }
    }

}
