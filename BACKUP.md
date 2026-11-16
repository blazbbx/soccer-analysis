# Mentés és visszaállítás

A `backup.sh` a `docker-compose-deploy.yml` stackjét menti, futás közben. Élesítés előtt olvasd végig, és
egyszer próbálj ki egy visszaállítást egy tartalék gépen: amit még sosem állítottál vissza, az csak remény.

## Mi kerül a mentésbe

| Mit | Hogyan | Hova |
|---|---|---|
| Az alkalmazás adatbázisa (`football_db`) | `pg_dump` | `<dátum>/football_db.dump` |
| A Keycloak adatbázisa (`keycloak_db`): felhasználók, jelszó-hashek, szerepkörök | `pg_dump` | `<dátum>/keycloak_db.dump` |
| Sportorvosi igazolások és klub-tájékoztatók (`medical-docs`, `club-documents`) | a MinIO adatkönyvtárából másolva, **titkosítva** | `<dátum>/minio-encrypted.tar.gz` |
| HTTPS tanúsítványok és a belső CA (`caddy_data`) | archívum | `<dátum>/caddy_data.tar.gz` |
| Nyers videók, streamek, klipek, tracking, pályaképek | növekményes másolat, egy közös minden napra | `minio-mirror/<bucket>/` |

**Miért két adatbázis?** Egy Postgres szerver fut (a `football-postgres` konténer), benne két külön
adatbázissal: az alkalmazásé és a Keycloaké. Mindkettő kell: `keycloak_db` nélkül visszaállítás után senki nem
tud belépni.

**Miért titkosítva a sportorvosi rész?** Ha a MinIO-n keresztül (S3 API-n) másolnánk, a MinIO visszafejtené a
fájlokat, és a mentésben olvasható egészségügyi adat lenne. Ezért ezek közvetlenül a lemezről, a MinIO által
titkosított formában kerülnek a mentésbe.

Egy dátumozott mappa csak akkor teljes, ha van benne `OK` fájl. A `BACKUP_KEEP_DAYS`-nél (alapból 14) régebbi
dátumozott mappák törlődnek. A tükör (`minio-mirror`) megtartja az alkalmazásban már törölt fájlokat is, így egy
véletlen törlés visszahozható; emiatt idővel nő (a nyers videók nagyok), figyeld a lemezt.

### Ami nincs a mentésben: a `.env`

A `.env` tartalmazza az összes jelszót és a `MINIO_KMS_SECRET_KEY`-t.

**Miért kell róla másolat?** Ha a szerver tönkremegy, a `.env` is vele veszik (a gitben nincs benne). Új
szerveren a mentésből csak ugyanazokkal az értékekkel lehet visszaállni:
- a sportorvosi fájlok a `MINIO_KMS_SECRET_KEY`-jel vannak titkosítva: **a pontos kulcs nélkül örökre
  olvashatatlanok** (a MinIO elutasítja a visszafejtést);
- a visszaállított adatbázisok és szolgáltatások ezekre a jelszavakra vannak beállítva.

**Miért külön a mentéstől?** A `.env` a kulcs a sportorvosi adatokhoz. Ha egy helyen van a mentéssel, aki elviszi
a mentés lemezét, mindent el tud olvasni. Tedd jelszókezelőbe (pl. Bitwarden, KeePass) vagy más biztonságos
helyre. (`BACKUP_COPY_ENV=1` mégis bemásolja minden dátumozott mappába, de csak akkor használd, ha maga a mentés
helye is védett.)

## Beállítás

### Mi kell hozzá a szerveren

A szkript Linuxra készült (sima `bash`, ami `docker` parancsokat futtat). Kell hozzá:
- `bash` és `docker` (a deploy miatt amúgy is megvannak);
- `flock` (a legtöbb Linuxon alapból fent van; nélküle is fut, csak a párhuzamos futás elleni védelem marad ki);
- olyan felhasználó, aki futtathat `docker` parancsot (root, vagy a `docker` csoport tagja).

A futtatás helye a repó mappája (a szkript ott keresi a `.env`-et), és a stacknek futnia kell.

### Hova menjen a mentés: `BACKUP_DIR`

Nem fájlban adod meg, hanem a parancs elején, futtatáskor:
```sh
BACKUP_DIR=/mnt/backup ./backup.sh
```
A `/mnt/backup` helyére annak a mappának az útvonalát írd, ahova menteni akarsz. Ez legyen **másik lemez vagy
másik gép** (USB-s külső lemez, hálózati meghajtó / NAS): a szerver saját lemezén lévő mentés vele együtt
veszik el. Ha nem adod meg, a szkript el sem indul, hanem szól, hogy hiányzik.

### Első futtatás kézzel

```sh
cd /utvonal/soccer-analysis
BACKUP_DIR=/mnt/backup ./backup.sh
```
Nézd meg a kimenetet és a mappát: legyen benne egy új dátumozott mappa `OK` fájllal.

### Napi automatikus futtatás: cron

A cron a Linux beépített időzítője: megadott időpontokban magától lefuttat egy parancsot, így nem kell minden
nap kézzel elindítani a mentést. A `crontab -e` paranccsal lehet szerkeszteni (annak a felhasználónak, aki
futtathat `docker`-t), és ezt a sort kell beleírni:
```
30 2 * * * cd /utvonal/soccer-analysis && BACKUP_DIR=/mnt/backup ./backup.sh >> /var/log/football-backup.log 2>&1
```
- `30 2 * * *`: minden nap 2:30-kor. Az öt mező: perc, óra, a hónap napja, hónap, a hét napja; a `*`
  jelentése „mindegyik”.
- `cd /utvonal/soccer-analysis`: belép a repó mappájába (a szkript ott keresi a `.env`-et).
- `BACKUP_DIR=/mnt/backup ./backup.sh`: lefuttatja a mentést.
- `>> /var/log/football-backup.log 2>&1`: a kimenetet (a hibákat is) egy naplófájlba írja, hogy utólag meg
  lehessen nézni, sikerült-e. Ha a felhasználó nem írhat a `/var/log`-ba, adj meg egy másik helyet, pl.
  `$HOME/football-backup.log`.

A két útvonalat (`/utvonal/soccer-analysis`, `/mnt/backup`) a saját szerveredhez kell igazítani.
Időnként nézz bele a naplóba, és ellenőrizd, hogy készül-e új mappa `OK` fájllal.

### További beállítások

Környezeti változóként, ugyanúgy a parancs elején:
- `BACKUP_KEEP_DAYS` (alapból 14): hány napig maradjanak meg a dátumozott mappák;
- `BACKUP_MIRROR=0`: kihagyja a nagy bucketek tükrözését;
- `BACKUP_COPY_ENV=1`: a `.env`-et is bemásolja (lásd fent).

Ha bármelyik lépés hibázik, a szkript nem nulla kilépési kóddal áll le.

## Visszaállítás

Új (vagy letörölt) szerveren, a repó mappájából. Lent `B` a visszaállítandó dátumozott mentés mappája, `M`
a mellette lévő `minio-mirror` mappa.

1. **Tedd vissza a `.env`-et** ugyanazokkal az értékekkel, mint korábban (ugyanazok a jelszavak, ugyanaz a
   `MINIO_KMS_SECRET_KEY`).

2. **Indítsd el csak a Postgrest**, és hagyd, hogy a setup létrehozza a `keycloak_db`-t:
   ```sh
   docker compose -f docker-compose-deploy.yml up -d postgres postgres-setup
   ```

3. **Állítsd vissza mindkét adatbázist** (felülírja, ami bennük van):
   ```sh
   docker exec -i football-postgres pg_restore -U admin -d football_db --clean --if-exists --no-owner < "$B/football_db.dump"
   docker exec -i football-postgres pg_restore -U admin -d keycloak_db --clean --if-exists --no-owner < "$B/keycloak_db.dump"
   ```

4. **Állítsd vissza a titkosított bucketeket** a MinIO adatkönyvtárába, leállított MinIO mellett:
   ```sh
   docker compose -f docker-compose-deploy.yml up -d minio      # létrehozza a kötetet
   docker compose -f docker-compose-deploy.yml stop minio
   docker run --rm --volumes-from football-minio -v "$B:/backup:ro" alpine:3.20 tar xzf /backup/minio-encrypted.tar.gz -C /data
   ```

5. **Állítsd vissza a tanúsítványokat** (nem kötelező: nélkülük a Caddy újakat készít, de `CADDY_TLS=internal`
   mellett akkor minden eszközre újra fel kell telepíteni az új gyökértanúsítványt):
   ```sh
   docker compose -f docker-compose-deploy.yml up --no-start proxy
   docker run --rm --volumes-from football-proxy -v "$B:/backup:ro" alpine:3.20 tar xzf /backup/caddy_data.tar.gz -C /data
   ```

6. **Indíts el mindent.** A `minio-setup` újra létrehozza a bucketeket, a jogosultságokat és a szolgáltatási
   fiókokat; a visszaállított sportorvosi fájlok a KMS kulccsal újra olvashatók:
   ```sh
   docker compose -f docker-compose-deploy.yml up -d --build
   ```

7. **Másold vissza a nagy bucketeket** a tükörből (a MinIO root felhasználóval, a `MINIO_ROOT_PASSWORD` a
   `.env`-ből jön):
   ```sh
   docker run --rm --network "$(docker inspect -f '{{range $n, $_ := .NetworkSettings.Networks}}{{$n}}{{end}}' football-minio)" \
     -v "$M:/mirror:ro" -e MINIO_ROOT_PASSWORD="<a .env-ből>" --entrypoint sh minio/mc:latest -c '
       mc alias set dst http://minio:9000 admin "$MINIO_ROOT_PASSWORD" >/dev/null &&
       for b in raw-videos hls-streams clips tracking-data field-detection; do
         [ -d "/mirror/$b" ] && mc mirror --quiet --overwrite "/mirror/$b" "dst/$b"; done'
   ```

8. **Ellenőrizd:** lépj be, nyiss meg egy meccset (a videó elindul), és nyiss meg egy sportorvosi igazolást
   olyan felhasználóval, aki láthatja.
