# Fake-Jedis
[![Build Status](https://travis-ci.org/vdurmont/fake-jedis.svg?branch=master)](https://travis-ci.org/vdurmont/fake-jedis)
[![License Info](http://img.shields.io/badge/license-The%20MIT%20License-brightgreen.svg)](https://github.com/vdurmont/fake-jedis/blob/master/LICENSE.md)

Simple Java library that **mimics** the behavior of a [redis](http://redis.io) server wrapped into a [Jedis](https://github.com/xetorthio/jedis) instance. Its purpose is to test *algorithms* involving the use of a redis database without starting an instance.

***Important note:*** You should NEVER use fake-jedis in a production or a performance testing context. It is designed to test your algorithms but won't behave as a real redis instance when you'll test concurrency, load, persistence, etc.

## Installation

Add the dependency to your maven project (or checkout the project and compile the jar).

```xml
<dependency>
  <groupId>com.vdurmont</groupId>
  <artifactId>fake-jedis</artifactId>
  <version>0.1.0</version>
  <scope>test</scope> <!-- You should not use Fake-Jedis if it is not a test! -->
<dependency>
```

## Usage

Simply instantiate a `FakeJedis` and use it as a regular `Jedis` client.
```java
Jedis jedis = new FakeJedis();
// Use your client
jedis.lpush("my_key", "my_value");
Long len = jedis.llen("my_key");
```

Do not hesitate to take a look at the [Jedis documentation](https://github.com/xetorthio/jedis/wiki) and/or the [Redis documentation](http://redis.io/commands).

## Supported commands

### FakeJedis class (extends redis.clients.jedis.Jedis)

* `void close()`
* `Long del(String)`
* `Long del(String...)`
* `Long unlink(String key)`
* `Long unlink(String... keys)`
* `Boolean exists(String)`
* `String get(String)`
* `String hget(String,String)`
* `Map<String,String> hgetAll(String)`
* `Long hincrBy(String,String,long)`
* `Long hset(String,String,String)`
* `Long hdel(String key, String... fields)`
* `Set<String> keys(String)`
* `Long sadd(String key, String... members)`
* `Set<String> smembers(String key)`
* `Boolean sismember(String key, String member)`
* `Long srem(String key, String... members)`
* `Long llen(String)`
* `String lpop(String)`
* `Long lpush(String,String...)`
* `List<String> lpush(String,long,long)`
* `Transaction multi()` (returns an instance of `FakeTransaction`)
* `String set(String,String)`
* `Long setnx(String,String)`

### FakeTransaction class (extends redis.clients.jedis.Transaction)

* `List<Object> exec()`
* `Response<Long> del(String)`
* `Response<Long> del(String...)`
* `Response<Long> hincrBy(String,String,long)`
* `Response<String> lpop(String)`
* `Response<Long> lpush(String,String...)`
* `Response<String> set(String,String)`

## License

See [LICENSE.md](./LICENSE.md)

## Contributing

Pull requests are welcome if you find a bug or if you want to implement a new method! Please provide tests ;)
