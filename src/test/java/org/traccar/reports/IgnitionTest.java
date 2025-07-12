package org.traccar.reports;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.traccar.config.Config;
import org.traccar.geofence.GeofenceGeometry;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.helper.model.GeofenceUtil;
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.Device;
import org.traccar.model.Geofence;
import org.traccar.model.Position;
import org.traccar.reports.model.IgnitionReportItem;
import org.traccar.storage.Storage;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IgnitionTest {

    @Mock
    private Storage storage;
    @Mock
    private Config config;

    private Device device;
    private Geofence geofence;
    private Ignition ignition;

    @BeforeEach
    public void setUp() {
        ignition = new Ignition(storage, config);

        device = new Device();
        device.setId(1L);
        device.setName("Test Device");

        geofence = mock(Geofence.class);
        when(geofence.getId()).thenReturn(100L);
        when(geofence.getName()).thenReturn("Test Zone");

        GeofenceGeometry geometry = mock(GeofenceGeometry.class);
        when(geometry.containsPoint(any(), any(), anyDouble(), anyDouble())).thenReturn(true);
        when(geofence.getGeometry()).thenReturn(geometry);
    }

    @Test
    public void test_ignition_On_Off_creates_report() throws Exception {
        Position start = createPosition(true, 10.0, 10.0, "2025-04-01T10:00:00Z");
        Position end = createPosition(false, 10.1, 10.1, "2025-04-01T11:00:00Z");

        try (
                MockedStatic<PositionUtil> positionUtil = Mockito.mockStatic(PositionUtil.class);
                MockedStatic<GeofenceUtil> geofenceUtil = Mockito.mockStatic(GeofenceUtil.class);
                MockedStatic<DeviceUtil> deviceUtil = Mockito.mockStatic(DeviceUtil.class)
        ) {
            deviceUtil.when(() ->
                    DeviceUtil.getAccessibleDevices(eq(storage), eq(1L), any(), any())
            ).thenReturn(List.of(device));

            positionUtil.when(() ->
                    PositionUtil.getPositions(eq(storage), eq(1L), any(), any())
            ).thenReturn(List.of(start, end));

            geofenceUtil.when(() ->
                    GeofenceUtil.getAllGeofences(eq(storage))
            ).thenReturn(List.of(geofence));

            Collection<IgnitionReportItem> result = ignition.getObjects(
                    1L,
                    List.of(1L),
                    null,
                    Date.from(Instant.parse("2025-04-01T00:00:00Z")),
                    Date.from(Instant.parse("2025-04-02T00:00:00Z")),
                    false
            );

            assertEquals(1, result.size());
            IgnitionReportItem item = result.iterator().next();
            assertEquals("Test Device", item.getDeviceName());
            assertEquals("Test Zone", item.getGeofence());
            assertEquals(3600000L, item.getDuration());
            assertTrue(item.getDistance() > 0);
            assertEquals(1.0, item.getEngineHours(), 0.0001);
        }
    }

    private Position createPosition(Boolean ignition, double lat, double lon, String isoTime) {
        Position p = new Position();
        p.setFixTime(Date.from(Instant.parse(isoTime)));
        p.setLatitude(lat);
        p.setLongitude(lon);
        if (ignition != null) {
            p.set(Position.KEY_IGNITION, ignition);
        }
        return p;
    }
}
