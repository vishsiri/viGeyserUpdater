# viGeyserUpdater

ตัวอัปเดตแบบ crash-safe สำหรับปลั๊กอินและ extension รอบ Geyser รองรับ **Paper, Folia, Spigot และ Velocity** จาก codebase เดียวกัน

ค่าเริ่มต้นดูแลไฟล์ต่อไปนี้:

| Component | Bukkit | Velocity | Source |
|---|---:|---:|---|
| GeyserModelEngine plugin | yes | — | GitHub Release `latest` |
| GeyserModelEngine extension | yes | yes | GitHub Release `latest` |
| GeyserUtils platform plugin | Spigot build | Velocity build | GitHub Release `latest` |
| GeyserUtils extension | yes | yes | GitHub Release `latest` |
| Boar extension | yes | yes | Modrinth (`geyser` artifact) |

## Requirements

- Java 21
- Paper/Spigot/Folia 1.20.6+ หรือ Velocity 3.4+
- Geyser ติดตั้งในชื่อโฟลเดอร์มาตรฐาน `plugins/Geyser-Spigot` หรือ `plugins/Geyser-Velocity` (แก้ path ใน config ได้)

## Build และติดตั้ง

```powershell
.\gradlew.bat clean test build
```

- Bukkit family: `bukkit/build/libs/viGeyserUpdater-Bukkit-1.0.0.jar`
- Velocity: `velocity/build/libs/viGeyserUpdater-Velocity-1.0.0.jar`

นำ JAR ที่ตรงกับ platform ไปใส่ใน `plugins/` แล้วเปิด server/proxy ครั้งแรก ระบบจะสร้าง `plugins/viGeyserUpdater/config.yml` ค่าเริ่มต้นเปิด auto-update ทุก 6 ชั่วโมงและติดตั้งให้อัตโนมัติ การอัปเดตที่ติดตั้งแล้วมีผลหลัง **full restart** เท่านั้น ไม่ควรใช้ plugin reloaders

ถ้าต้องการเฉพาะ manual mode:

```yaml
automatic:
  enabled: false
```

หรือให้ตรวจอัตโนมัติแต่ไม่ติดตั้ง:

```yaml
automatic:
  enabled: true
  apply: false
```

## Commands

Permission: `vigeyserupdater.admin` (ค่าเริ่มต้น Bukkit ให้ OP; Velocity ใช้ permission provider ของระบบ)

```text
/geyserupdates status
/geyserupdates check [artifact|all]
/geyserupdates update [artifact|all]
/geyserupdates reload
```

Alias: `/gupdates`

## Safety model

ทุก update ทำตามลำดับนี้:

1. ตรวจ HTTPS host allowlist, redirect ทุก hop, timeout, `Content-Length` และ byte limit
2. ดาวน์โหลดเป็นไฟล์ชั่วคราวและ force ข้อมูลลง storage
3. ตรวจ SHA-256 (เมื่อ upstream ส่ง digest) และเปิด JAR เพื่อตรวจโครงสร้าง/entry
4. เขียน transaction journal แบบ atomic
5. สลับ `current -> backup -> new` ด้วย atomic move เมื่อ filesystem รองรับ
6. ถ้า process หยุดกลางทาง startup ถัดไปจะเลือกคืนไฟล์เก่าหรือรักษาไฟล์ใหม่ตาม state ที่พบ

ไฟล์ `state.json` และ transaction journal ก็เขียนผ่าน temporary file + replace เช่นกัน งาน network/file I/O อยู่บน worker thread เดี่ยว ไม่บล็อก main thread และไม่ใช้ Bukkit scheduler สำหรับ polling จึงใช้ได้กับ Folia

ข้อจำกัด: crash tests จำลอง exception หลัง transaction checkpoints ทั้งสามจุดและ first-install recovery ไม่ใช่การตัดไฟจริง ความทนทานสูงสุดยังขึ้นกับ atomic-move/fsync semantics ของ filesystem และ storage controller ที่ใช้

บน Bukkit ตัว updater ถูกตั้งให้โหลดช่วง `STARTUP` และก่อน Geyser/GeyserUtils/GeyserModelEngine เพื่อลดปัญหา JAR lock บน Windows อย่างไรก็ตาม Velocity ไม่รับประกันลำดับ classloader แบบเดียวกัน; หาก filesystem ปฏิเสธการสลับ JAR ที่กำลังเปิดอยู่ ระบบจะรายงาน `failed` และรักษาไฟล์เดิมไว้ ให้หยุด proxy แล้วแทนไฟล์แบบ manual ในกรณีนั้น

## Configuration notes

- `${plugins}` คือ `<server-root>/plugins`
- `${geyser}` คือ `plugins/Geyser-Spigot` บน Bukkit family และ `plugins/Geyser-Velocity` บน Velocity
- `existing-regex` ช่วยหา JAR เดิมที่มีเลขเวอร์ชันในชื่อ ถ้าพบมากกว่าหนึ่งไฟล์ระบบจะหยุด artifact นั้นแทนการเดา
- ใส่ `GITHUB_TOKEN` เป็น environment variable ได้เพื่อเพิ่ม GitHub API rate limit; ไม่ต้องเก็บ token ใน YAML
- เมื่อ host ตอบไม่ได้, timeout, HTTP 429 หรือ HTTP 5xx ระบบ retry แบบ exponential backoff ตาม `retry-attempts`/`retry-delay-seconds`; หากยังล้มเหลว artifact จะเป็น `failed`, JAR/state เดิมไม่ถูกแตะ และ automatic scheduler จะลองใหม่ในรอบถัดไป
- ปิด artifact รายตัวด้วย `enabled: false`
- หากใช้ชื่อโฟลเดอร์ Geyser ต่างจากมาตรฐาน ให้เปลี่ยน `destination` ของ extension เป็น path แบบ relative จาก server root

## Tests

ชุดปกติครอบคลุม transaction recovery ทุก checkpoint, first install, JAR validation, destination matching และ path traversal:

รวมถึงจำลอง update host ปิดพอร์ต/เชื่อมต่อไม่ได้ เพื่อตรวจ retry exhaustion, fail-closed host allowlist และยืนยันว่า JAR เดิมไม่มีการแก้ไขหรือสร้าง transaction ค้าง

```powershell
.\gradlew.bat clean test
```

Live smoke tests จะ resolve API ปัจจุบัน, ดาวน์โหลด GeyserUtils asset จริง, validate และติดตั้งลง temporary directory รวมถึง resolve Boar จาก Modrinth:

```powershell
$env:VI_LIVE_UPDATE_TEST='true'
.\gradlew.bat clean test --no-build-cache
```

## Design

`common` มี updater engine, source providers, validation, state และ crash-safe installer โดยไม่อ้าง Bukkit/Velocity API ส่วน `bukkit` และ `velocity` เป็น adapter บาง ๆ สำหรับ lifecycle, command, permission และ message dispatch เท่านั้น ทำให้เพิ่ม provider หรือ platform ภายหลังโดยไม่รวมทุกอย่างไว้ใน God Class

โปรเจกต์นี้เขียนใหม่โดยศึกษาเฉพาะวิธีแจก artifact และตำแหน่งติดตั้งจาก upstream ไม่คัดลอก source code ของ GeyserModelEngine, GeyserUtils หรือ Boar
