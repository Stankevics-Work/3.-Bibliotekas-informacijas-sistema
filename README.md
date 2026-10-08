# LibInfo - bibliotēkas menedžmenta sistēma

## Palaišana

1. Izpako arhīvu.
2. NetBeans izvēlies **File → Open Project** un atver projektu `3.-Bibliotekas-informacijas-sistema`.
3. Properties → Libraries → Java Platform izvēlies JDK 21 vai jaunāku.
4. Izpildi **Clean and Build**, pēc tam **Run Project**.
5. Pirmajā palaišanas reizē programma automātiski izveido Derby datubāzi **`lib_db`** projekta mapē, izveido visu shēmu, ievieto sākotnējos projekta datus un uzreiz atver pieteikšanās logu.
6. Nākamajās palaišanas reizēs tā pati `lib_db` datubāze tiek izmantota, un dati netiek dzēsti.

Nav vajadzīgs Maven vai atsevišķs Derby serveris. Derby strādā iegultā (embedded) režīmā no `lib/derby.jar`.

## Datu bāze

Projektā ir paredzēta **viena vienīga DB mape — `lib_db`**. `data/LibInfo` nav nepieciešama un projektā vairs netiek izmantota.

Pirmās palaišanas laikā tiek automātiski izveidotas piecas tabulas, skati un trigeri no `src/schema.sql`, pēc tam tiek sagatavoti visi iepriekš pārnestie dati:

- 4 lietotāji;
- 2 grāmatas;
- 2 eksemplāri;
- 1 izsniegums;
- 1 rezervācija.

Tiek saglabāti arī savstarpējie ierakstu sasaistes dati un sākotnējie datumi. Sākotnējo datu ievietošana ir idempotenta — ieraksti netiek dublēti.

### Noklusējuma administrators

**Lietotājvārds:** `admin`  
**Parole:** `admin123`

Administratora parole tiek nodrošināta ar BCrypt jaucējvērtību. Pārējie pārnestie lietotāji saglabā savus oriģinālos paroles jaucējumus.

### Svarīgi

NetBeans projekta atvēršana pati par sevi Java kodu neizpilda; automātiska DB izveide notiek pie **Run Project**. `lib_db` mape ZIP arhīvā ir tikai kā DB atrašanās vieta; Derby pati izveido tajā savus failus pirmajā palaišanā.

## GUI arhitektūra

Saglabāts prasītais izkārtojums: **1 `JFrame` (`LibInfoSaskarne`) + 11 `JDialog` logi**.

Navigācija: `Login → Main → apakšlogi`, aizverot apakšlogu atgriežas `Main`. Esošais NetBeans dizains un komponentu izkārtojums nav pārbūvēts no jauna.
