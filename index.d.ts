// Type definitions for @react-native-google-signin/google-signin 6.0
// Project: https://github.com/react-native-community/google-signin
// Definitions by: Jacob Froman <https://github.com/j-fro>
//                 Michele Bombardi <https://github.com/bm-software>
//                 Christian Chown <https://github.com/christianchown>
//                 Eric Chen <https://github.com/echentw>

import * as React from 'react';
import { StyleProp, ViewProps, ViewStyle } from 'react-native';

export interface GoogleOneTapSignInButtonProps extends ViewProps {
  style?: StyleProp<ViewStyle>;
  size?: GoogleSigninButton.Size;
  color?: GoogleSigninButton.Color;
  disabled?: boolean;
  onPress?(): void;
}

export class GoogleOneTapSignInButton extends React.Component<GoogleOneTapSignInButtonProps> {
  constructor(props: GoogleOneTapSignInButtonProps);
}

export namespace GoogleSigninButton {
  enum Size {
    Standard,
    Wide,
    Icon,
  }

  enum Color {
    Light,
    Dark,
  }
}

export interface HasPlayServicesParams {
  /**
   * When showPlayServicesUpdateDialog is true, the user will be prompted to
   * install Play Services if on Android and they are not installed.
   * Default is true
   */
  showPlayServicesUpdateDialog?: boolean;
}

export interface ConfigureParams {
  /**
   * The Google API scopes to request access to. Default is email and profile.
   */
  scopes?: string[];

  /**
   * Web client ID from Developer Console. Required for offline access
   */
  webClientId?: string;

  /**
   * If you want to specify the client ID of type iOS
   */
  iosClientId?: string;

  /**
   * If you want to specify a different bundle path name for the GoogleService-Info, e.g. GoogleService-Info-Staging
   */

  googleServicePlistPath?: string;

  /**
   * Must be true if you wish to access user APIs on behalf of the user from
   * your own server
   */
  offlineAccess?: boolean;

  /**
   * Specifies a hosted domain restriction
   */
  hostedDomain?: string;

  /**
   * iOS ONLY.[iOS] The user's ID, or email address, to be prefilled in the authentication UI if possible.
   * https://developers.google.com/identity/sign-in/ios/api/interface_g_i_d_sign_in.html#a0a68c7504c31ab0b728432565f6e33fd
   */
  loginHint?: string;

  /**
   * ANDROID ONLY. If true, the granted server auth code can be exchanged for an access token and a refresh token.
   */
  forceCodeForRefreshToken?: boolean;

  /**
   * ANDROID ONLY. An account name that should be prioritized.
   */
  accountName?: string;

  /**
   * If true, enables Google Token ID based auth during sign in.
   * (defaults=true)
   */
  useTokenSignIn?: boolean;

  /**
   * If true, enables Username/Password based auth during sign in.
   * (default=false)
   */
  usePasswordSignIn?: boolean;
}

export interface User {
  user: {
    id: string;
    name: string | null;
    familyName: string | null;
    givenName: string | null;
    password: string | null;
    photo: string | null;
  };
  idToken: string | null;
}

export namespace GoogleOneTapSignIn {
  /**
   * Check if the device has Google Play Services installed. Always resolves
   * true on iOS
   */
  function hasPlayServices(params?: HasPlayServicesParams): Promise<boolean>;

  /**
   * Configures the library for login. MUST be called before attempting login
   */
  function configure(params?: ConfigureParams): void;

  /**
   * Returns a Promise that resolves with the current signed in user or rejects
   * if not signed in.
   */
  function signInSilently(): Promise<User>;

  /**
   * Prompts the user to sign in with their Google account. Resolves with the
   * user if successful.
   */
  function signIn(): Promise<User>;

  /**
   * Signs the user out.
   */
  function signOut(): Promise<null>;

  /**
   * Save password after normal sign up / sign in
   */
  function savePassword(userId: string, password: string): Promise<boolean>;
}

export const statusCodes: {
  SIGN_IN_CANCELLED: string;
  IN_PROGRESS: string;
  PLAY_SERVICES_NOT_AVAILABLE: string;
  SIGN_IN_REQUIRED: string;
};
