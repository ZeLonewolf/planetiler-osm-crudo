package com.streetferret.crudo;

import static com.onthegomap.planetiler.expression.Expression.matchField;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureCollector.Feature;
import com.onthegomap.planetiler.Profile;
import com.onthegomap.planetiler.expression.MultiExpression;
import com.onthegomap.planetiler.expression.MultiExpression.Index;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.json.JSONArray;
import org.json.JSONException;

public class CrudoProfile implements Profile {

  private final List<String> keyList = Arrays
    .asList("building", "natural", "landuse", "boundary", "route", "place", "amenity", "tourism", "healthcare", "shop",
      "craft", "leisure", "golf", "barrier", "man_made", "waterway", "highway", "public_transit", "railway", "power",
      "aerialway", "aeroway", "historic", "military", "emergency", "office", "geological", "shop", "sport",
      "advertising", "airmark", "cemetery", "entrance", "indoor", "landcover", "pipeline", "playground",
      "traffic_sign");


  private final Index<String> layerIndex = MultiExpression.of(
    keyList.stream()
      .map(key -> MultiExpression.entry(key, matchField(key)))
      .toList())
    .index();

  private final Map<String, Set<String>> layerKeyMap = new HashMap<>();

  public CrudoProfile() {
    keyList.forEach(this::loadTopKeysInLayer);
  }

  private void loadTopKeysInLayer(String layer) {
    var keyComboUrl = keyComboUrl(layer);
    try {
      var thisLayerKeys = JsonUtil.readJsonFromUrl(keyComboUrl);
      var data = (JSONArray) thisLayerKeys.get("data");
      var allowableKeys = data.toList()
        .stream()
        .map(Map.class::cast)
        .map(jsonObj -> jsonObj.get("other_key"))
        .map(Object::toString)
        .collect(Collectors.toCollection(HashSet::new));
      allowableKeys.add(layer);
      layerKeyMap.put(layer, allowableKeys);
    } catch (JSONException | IOException e) {
      e.printStackTrace();
    }
  }

  private String keyComboUrl(String key) {
    return "https://taginfo.openstreetmap.org/api/4/key/combinations?key=" + key +
      "&filter=all&sortname=to_count&sortorder=desc&page=1&rp=10&qtype=other_key";
  }

  private void configFeature(String layer, SourceFeature src, Feature desc) {

    var allowableKeys = layerKeyMap.get(layer);

    src.tags().forEach((key, value) -> {
      if (key != null && allowableKeys.contains(key)) {
        desc.setAttr(key, value);
      }
    });
    desc.setMinPixelSize(4);
  }

  @Override
  public void processFeature(SourceFeature sourceFeature, FeatureCollector features) {
    try {
      if ("water".equals(sourceFeature.getSource())) {
        features.polygon("water")
          .setMinPixelSize(4)
          .setAttr("water", "ocean");
        return;
      }

      var matches = layerIndex.getMatches(sourceFeature);
      if (matches.isEmpty()) {
        return; //TODO make misc layer
      }
      var layer = matches.isEmpty() ? "miscellaneous" : matches.get(0);

      if (sourceFeature.isPoint()) {
        Feature point = features.point(layer);
        configFeature(layer, sourceFeature, point);
      } else if (sourceFeature.canBePolygon()) {
        Feature centroid = features.centroid(layer);
        Feature area = features.polygon(layer);
        configFeature(layer, sourceFeature, centroid);
        configFeature(layer, sourceFeature, area);
      } else if (sourceFeature.canBeLine()) {
        Feature line = features.line(layer);
        configFeature(layer, sourceFeature, line);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public String name() {
    return "OpenStreetMap Crudo";
  }

}
