import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId:    'com.aura.musicplayer',
  appName:  'Aura',
  webDir:   'src',
  server: {
    androidScheme: 'https',
    cleartext: false,
  },
  android: {
    allowMixedContent: false,
    captureInput:      true,
    webContentsDebuggingEnabled: false,  // set true for dev
    minWebViewVersion: 90,
    backgroundColor:  '#08080d',
  },
  plugins: {
    SplashScreen: {
      launchShowDuration:   0,          // We handle our own JS splash
      backgroundColor:      '#08080d',
      androidSplashResourceName: 'splash',
      showSpinner: false,
    },
    StatusBar: {
      style:           'DARK',
      backgroundColor: '#08080d',
      overlaysWebView: true,            // Immersive — content goes under status bar
    },
    LocalNotifications: {
      smallIcon:   'ic_notification',
      iconColor:   '#fc3d6a',
      sound:       'none',
    },
    Haptics: {
      // uses system haptic engine
    },
  },
};

export default config;
