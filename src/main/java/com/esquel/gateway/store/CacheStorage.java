package com.esquel.gateway.store;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public class CacheStorage<K, V> implements BaseStorage<K, V> {

  private final Cache<K, V> cache;

  public CacheStorage(Supplier<Cache<K,V>> supplier) {
    this.cache = supplier.get();
  }

  @Override
  public void add(K key, V value) {
    cache.put(key, value);
  }

  @Override
  public V get(K key) {
    return cache.getIfPresent(key);
  }

  @Override
  public boolean isEmpty() {
    return cache.size() == 0;
  }

  @Override
  public void remove(K key) {
    cache.invalidate(key);
  }

  @Override
  public void removeAll() {
    cache.invalidateAll();
  }

  @Override
  public boolean exists(K key) {
    return cache.asMap().containsKey(key);
  }

  @Override
  public ImmutableMap<K, V> getAll() {
    ImmutableMap.Builder<K, V> builder = ImmutableMap.builder();
    cache.asMap().keySet().forEach(k -> {
      V ifPresent = cache.getIfPresent(k);
      if (Objects.nonNull(ifPresent)) {
        builder.put(k, ifPresent);
      }
    });
    return builder.build();
  }

  public List<V> values(){
    return ImmutableList.copyOf(cache.asMap().values());
  }

}
