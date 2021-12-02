package com.esquel.gateway.store;

import com.google.common.collect.ImmutableMap;

public interface BaseStorage<K,V>{

  void add(K key, V value);

  V get(K key);

  boolean isEmpty();

  void remove(K key);

  void removeAll();

  boolean exists(K key);

  ImmutableMap<K, V> getAll();
}
