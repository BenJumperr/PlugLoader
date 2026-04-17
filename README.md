# ⚙️ PlugLoader

> **Paper & Purpur (1.21.11) için dinamik, Maven tabanlı plugin yükleyici**

PlugLoader, klasik plugin sistemini genişleterek **sunucu restart atmadan plugin yönetimi**, **bağımsız çalışma ortamı** ve **runtime dependency yükleme** sağlar.

---

## 🚀 Temel Özellikler

### 🔹 Dinamik Plugin Sistemi

* `plugins/PlugLoader/plugins/` klasöründeki tüm geçerli yapılar otomatik algılanır
* Sunucu başlatılırken:

  ```
  [PlugLoader] Founded X plugin.
  ```
* Aktif pluginler otomatik başlatılır

---

### 🔹 Plugin Durum Yönetimi

* Açık pluginler:

  ```
  [PlugLoader] Starting X plugin
  ```
* Kapalı pluginler:

  ```
  [PlugLoader] It's Not Running Because X Plugins Are Disabled
  ```

---

### 🔹 Plugin Kimlik Sistemi (pluginid)

Her plugin tamamen izole çalışır:

```
plugins/PlugLoader/
├── plugins/<pluginid>/
├── libs/<pluginid>/
│   ├── dependencies/
│   └── repositories/
```

✔ Çakışma önlenir
✔ Her plugin bağımsızdır

---

### 🔹 Maven Dependency Sistemi

`config.yml` içinde plugin bazlı yapı:

```yaml
plugins:
  example:
    dependencies:
      - org.jetbrains.kotlin:kotlin-stdlib:1.9.0
    repositories:
      - https://repo1.maven.org/maven2/
```

---

### 🔹 Runtime Yükleme Sistemi

Dependency yüklenirken:

```
[PlugLoader] Downloading dependencies 25%
```

Tamamlandığında:

```
[PlugLoader] Dependencies have been successfully loaded.
```

Aynı sistem repositories için de geçerlidir:

```
[PlugLoader] Downloading repositories 40%
[PlugLoader] Repositories have been successfully loaded.
```

---

### 🔹 Cache Sistemi

Ortak dependency varsa:

```
plugins/PlugLoader/libs/cache/
```

✔ Depolama tasarrufu
✔ Tekrar indirme engellenir

---

### 🔹 Sunucu API Kontrolü

Duruma göre:

* Kurulu değilse:

  ```
  [PlugLoader] Plugins in the server are loading...
  ```

* Kurulum tamam:

  ```
  [PlugLoader] Server plugins installed.
  ```

* Uyumsuzluk varsa:

  ```
  [PlugLoader] Incompatibility detected in installed plugins! Repairing installed plugins...
  ```

* Tamir sonrası:

  ```
  [PlugLoader] Repair completed successfully!
  ```

---

### 🔹 Logging Sistemi

Plugin içindeki tüm loglar otomatik prefix alır:

```
[PlugLoader] Hello World!
```

---

## 📂 Klasör Yapısı

```
plugins/
└── PlugLoader/
    ├── config.yml
    ├── messages.yml
    ├── plugins/
    │   └── <pluginid>/
    ├── libs/
    │   ├── <pluginid>/
    │   ├── cache/
    │   └── mandatory/
```

---

## 🧠 Güvenli Plugin Algılama

Her klasör plugin sayılmaz.

✔ Geçerli olması için:

* `plug.json` veya benzeri metadata bulunmalı

---

## ⚠️ Reload Sistemi

Tam restart yerine kontrollü yapı:

* `/plugloader reload plugins`
* `/plugloader reload deps`

Tam restart:

```
/plugloader restart
```

---

## 🧩 Komutlar

| Komut                      | Açıklama                     | Permission               |
| -------------------------- | ---------------------------- | ------------------------ |
| `/plugloader`              | Yardım menüsü                | `plugloader.help`        |
| `/plugloader list`         | Pluginleri listeler          | `plugloader.list`        |
| `/plugloader enable <id>`  | Plugin açar                  | `plugloader.enable`      |
| `/plugloader disable <id>` | Plugin kapatır               | `plugloader.disable`     |
| `/plugloader reload <id>`  | Plugin yeniden başlatır      | `plugloader.reload`      |
| `/plugloader restart`      | Tüm sistemi yeniden başlatır | `plugloader.restart`     |
| `/plugloader status`       | Aktif/pasif sayısı           | `plugloader.status`      |
| `/plugloader info <id>`    | Plugin detayları             | `plugloader.info`        |
| `/plugloader compile <id>` | Maven build                  | `plugloader.compile`     |
| `/plugloader cache clear`  | Cache temizler               | `plugloader.cache.clear` |
| `/plugloader create <id>`  | Yeni plugin oluşturur        | `plugloader.create`      |

Aliaslar:

```
/ploader
/pload
/plugload
```

---

## 🏁 Amaç

PlugLoader:

* Plugin geliştirmeyi hızlandırır
* Sunucu restart ihtiyacını azaltır
* Büyük sistemler için modüler yapı sağlar
* Her plugini bağımsız çalıştırır

---

## ⚡ Desteklenen Platformlar

* Paper
* Purpur

---

## 📌 Not

Bu sistem:

* Küçük scriptlerden
* Büyük Maven projelerine kadar
  ölçeklenebilir bir yapı sunar.

---
