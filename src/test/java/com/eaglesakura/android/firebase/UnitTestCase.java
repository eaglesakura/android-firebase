package com.eaglesakura.android.firebase;


import com.eaglesakura.android.AndroidSupportTestCase;

import org.junit.runner.RunWith;

import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, packageName = BuildConfig.APPLICATION_ID, sdk = 23)
public abstract class UnitTestCase extends AndroidSupportTestCase {
}
