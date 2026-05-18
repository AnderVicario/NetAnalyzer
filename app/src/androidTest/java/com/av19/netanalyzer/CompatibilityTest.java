package com.av19.netanalyzer;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.Manifest;
import android.os.Build;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;

import com.av19.netanalyzer.data.DeviceInfo;
import com.av19.netanalyzer.discovery.DiscoveryMethod;
import com.av19.netanalyzer.discovery.FakeDiscovery;
import com.av19.netanalyzer.service.ScanService;
import com.av19.netanalyzer.ui.main.MainActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CompatibilityTest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    // FIX Bug 1: Extendemos a >= 33 para cubrir API 36 y futuras versiones
    @Rule
    public GrantPermissionRule permissionRule = providePermissionsByAPI();

    private static GrantPermissionRule providePermissionsByAPI() {
        if (Build.VERSION.SDK_INT >= 33) {
            return GrantPermissionRule.grant(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.NEARBY_WIFI_DEVICES
            );
        } else {
            return GrantPermissionRule.grant(
                    Manifest.permission.ACCESS_FINE_LOCATION
            );
        }
    }

    @Before
    public void setupFakeDiscovery() {
        // FIX Bug 2: Extendemos a API 32+ para limpiar overlays/keyguards molestos de google_apis
        if (Build.VERSION.SDK_INT >= 32) {
            try {
                UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
                device.executeShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS");
                device.executeShellCommand("wm dismiss-keyguard");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Crear dispositivos falsos
        DeviceInfo device1 = new DeviceInfo("192.168.1.10", "AA:BB:CC:DD:EE:FF", "FakeVendor", Arrays.asList(80, 443));
        device1.setHostname(new DeviceInfo.PriorityValue(1, "FakePhone"));
        DeviceInfo device2 = new DeviceInfo("192.168.1.1", null, "RouterCorp", Arrays.asList(53));
        device2.setIsGateway(true);
        List<DiscoveryMethod> fakeMethods = Arrays.asList(new FakeDiscovery(Arrays.asList(device1, device2)));

        ScanService.setTestDiscoveryMethods(fakeMethods);
    }

    @Test
    public void fullScanAndInventoryShowsFakeDevices() throws InterruptedException {
        // FIX para API 33/34 (window-token=null):
        // Damos un respiro de 2 segundos antes de la primera acción de Espresso.
        // Esto permite que la actividad se asiente en el Window Manager tras la supresión de diálogos.
        Thread.sleep(10000);

        // Pulsar el botón SCAN
        onView(withId(R.id.btn_scan)).perform(click());

        // Esperar a que termine el escaneo asíncrono
        Thread.sleep(15000);

        // Verificar contador de dispositivos
        onView(withId(R.id.tv_devices_count)).check(matches(withText("2")));

        // Ir a la pestaña Devices y comprobar que aparecen los dispositivos falsos
        onView(withId(R.id.nav_devices)).perform(click());
        onView(withText("FakePhone")).check(matches(isDisplayed()));
        onView(withText("192.168.1.10")).check(matches(isDisplayed()));
        onView(withText("● GATEWAY")).check(matches(isDisplayed()));
    }

    @After
    public void tearDown() {
        ScanService.clearTestDiscoveryMethods();
    }
}