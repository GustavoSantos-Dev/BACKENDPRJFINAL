package com.empresa.sistema.service;

import com.empresa.sistema.dto.*;
import com.empresa.sistema.entity.Device;
import com.empresa.sistema.exception.ApiException;
import com.empresa.sistema.repository.DeviceRepository;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeviceService {
  private final DeviceRepository devices;

  public DeviceService(DeviceRepository devices) {
    this.devices = devices;
  }

  public static String hash(String key) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public Device authenticate(String id, String key) {
    Device d = devices.findById(id).orElseThrow(() -> unauthorized());
    if (key == null
        || key.length() > 256
        || !d.isActive()
        || !MessageDigest.isEqual(
            hash(key).getBytes(StandardCharsets.US_ASCII),
            d.getApiKeyHash().getBytes(StandardCharsets.US_ASCII))) throw unauthorized();
    return d;
  }

  private ApiException unauthorized() {
    return new ApiException(
        HttpStatus.UNAUTHORIZED, "DEVICE_UNAUTHORIZED", "Dispositivo não autorizado");
  }

  public List<Responses.DeviceView> list() {
    return devices.findAll().stream().map(Responses.DeviceView::of).toList();
  }

  public Responses.DeviceKey create(Requests.DeviceCreate r) {
    if (devices.existsById(r.deviceId()))
      throw ApiException.conflict("DEVICE_EXISTS", "Dispositivo já cadastrado");
    Device d = new Device();
    d.setId(r.deviceId());
    d.setName(r.name());
    return issue(d);
  }

  public Responses.DeviceKey rotate(String id) {
    Device d = devices.findLockedById(id).orElseThrow(() -> ApiException.missing("Dispositivo"));
    return issue(d);
  }

  private Responses.DeviceKey issue(Device d) {
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    String key = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    d.setApiKeyHash(hash(key));
    devices.save(d);
    return new Responses.DeviceKey(d.getId(), key);
  }

  public void deactivate(String id) {
    devices
        .findLockedById(id)
        .orElseThrow(() -> ApiException.missing("Dispositivo"))
        .setActive(false);
  }
}
