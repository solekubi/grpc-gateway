package com.esquel.gateway.store;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MapStorage<K, V> implements BaseStorage<K, V> {

  private final Map<K, V> map = new ConcurrentHashMap<>();

  @Override
  public void add(K key, V value) {
    map.put(key, value);
  }

  @Override
  public V get(K key) {
    return map.get(key);
  }

  @Override
  public void remove(K key) {
    map.remove(key);
  }

  @Override
  public void removeAll() {
    map.clear();
  }

  @Override
  public boolean isEmpty() {
    return map.size() == 0;
  }

  @Override
  public boolean exists(K key) {
    return map.containsKey(key);
  }

  @Override
  public ImmutableMap<K, V> getAll() {
    return ImmutableMap.copyOf(map);
  }

  public Set<K> keys(){
    return map.keySet();
  }

  public List<V> values(){
    return ImmutableList.copyOf(map.values());
  }
}
