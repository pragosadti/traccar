package org.traccar.reports;

import jakarta.inject.Inject;
import org.traccar.config.Config;
import org.traccar.helper.DistanceCalculator;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.helper.model.GeofenceUtil;
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.Device;
import org.traccar.model.Geofence;
import org.traccar.model.Position;
import org.traccar.reports.model.IgnitionReportItem;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.locationtech.jts.geom.Point;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.List;

public class Ignition {

    private final Storage storage;
    private final Config config;

    @Inject
    public Ignition(Storage storage, Config config) {
        this.storage = storage;
        this.config = config;
    }

    private IgnitionReportItem calculateIgnitionReport(
            Device device, long geofenceId, Position start, Position end) throws StorageException {
        IgnitionReportItem report = new IgnitionReportItem();
        report.setDeviceId(device.getId());
        report.setDeviceName(device.getName());
        report.setGeofence(GeofenceUtil.getAllGeofences(storage).stream().filter(geofence -> geofence.getId() == geofenceId).toList().get(0).getName());
        report.setStartTime(start.getFixTime());
        report.setEndTime(end.getFixTime());
        report.setDuration(end.getFixTime().getTime() - start.getFixTime().getTime());

        double distance = DistanceCalculator.distance(
                start.getLatitude(), start.getLongitude(),
                end.getLatitude(), end.getLongitude());

        report.setDistance(distance);
        return report;
    }

    private List<IgnitionReportItem> calculateDeviceReport(Device device, Date from, Date to) throws StorageException {
        List<IgnitionReportItem> result = new ArrayList<>();
        List<Position> positions  = PositionUtil.getPositions(storage, device.getId(), from, to);
        List<Geofence> geofences = GeofenceUtil.getAllGeofences(storage);

        if (!positions.isEmpty()) {
            int startIndex = -1;
            long startGeofence = -1;

            for (int i = 0; i < positions.size(); i++) {
                Position position = positions.get(i);
                Boolean ignition = position.getBoolean(Position.KEY_IGNITION);
                // Get geofence for position
                long geofence = getGeofenceForPosition(geofences, position);

                if (startIndex >= 0) {
                    if (ignition != null && !ignition || geofence != startGeofence) {
                        result.add(calculateIgnitionReport(device, startGeofence, positions.get(startIndex), position));
                        startIndex = -1;
                        startGeofence = -1;
                    }
                }

                if (startIndex < 0 && ignition != null && ignition && geofence > 0) {
                    startIndex = i;
                    startGeofence = geofence;
                }
            }

            if (startIndex >= 0 && startIndex < positions.size()) {
                result.add(calculateIgnitionReport(device, startGeofence, positions.get(startIndex), positions.get(positions.size() - 1)));
            }
        }
        return result;
    }

    private long getGeofenceForPosition(List<Geofence> geofences, Position position) {
        for (Geofence geofence : geofences) {
            if (geofence.getGeometry().containsPoint(config, geofence, position.getLatitude(), position.getLongitude())) {
                return geofence.getId();  // Return first matching geofence
            }
        }

        return -1;
    }

    public Collection<IgnitionReportItem> getObjects(long userId, Collection<Long> deviceIds,
                                                     Collection<Long> groupIds, Date from, Date to) throws SQLException, StorageException {
        List<IgnitionReportItem> result = new ArrayList<>();
        for (Device device : DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds)) {
            result.addAll(calculateDeviceReport(device, from, to));
        }
        return result;
    }

}
