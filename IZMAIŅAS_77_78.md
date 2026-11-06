# 77.–78. nodarbība — validācija un GUI–DAO–DB pārbaude

## Mērķis
Pārbaudīt kļūdainas ievades un robežgadījumus, pārbaudīt Java Swing GUI → DAO → Apache Derby datubāzes mijiedarbību, reģistrēt atklātās problēmas un novērst tās. Pārskatīti arī 18 komentāri failā `3_PD2_PA_Stankevics_Anisimovs_2026.docx`.

## Veiktie labojumi

| Problēma / komentārs | Risinājums / pārbaude |
| --- | --- |
| Nav vienkāršas izlogošanās | Izvēlnē **Fails → Izlogoties** notīra sesiju un atver pieteikšanās dialogu. Pārbauda `QuickGuiFlowTest`. |
| Lasītājs neredz savus izsniegumus | Lasītājam galvenā poga **Manas grāmatas** atver tikai viņa aktīvos izsniegumus ar termiņiem; atgriešanas darbība ir paslēpta. DAO filtrē pēc lietotāja ID. |
| Kavētās grāmatas/termiņš | Termiņš tiek saglabāts `izsniegums.termins`, kavētie atrodami **Izsniegums → Kavētie izsniegumi**; jaunā demonstrācijas datubāzē viens sākotnējais izsniegums ir kavēts. Stingri pārbauda datuma pareizību, pagātni un termiņu līdz vienam gadam. |
| Vairākus eksemplārus nevar pievienot vienā darbībā | Pievienojot grāmatu ar pieejamību, saglabāšanas dialogā izvēlas 1–100 eksemplārus. DAO tos izveido vienā transakcijā; 0 iespējams ne-pieejamai grāmatai. |
| Dzēšot izsniegtu grāmatu redzams tehnisks kļūdas teksts | DAO iepriekš pārbauda saistītos izsniegumus un rezervācijas; rāda lietotājam saprotamu ziņojumu. Neizmantotie eksemplāri tiek dzēsti ar grāmatu vienā transakcijā. |
| Rezervācija dublē paša lasītāja izsniegto grāmatu | DAO nepieļauj rezervāciju, ja lasītājam jau ir aktīvs šīs grāmatas izsniegums; GUI izvēlnē tādu grāmatu nepiedāvā. |
| Rezervācija varēja kļūt „izpildīta” bez grāmatas izsniegšanas | Darbinieks izvēlas pieejamu eksemplāru un termiņu, tiek izmantots `fulfillReservation`; `IzsniegumsDAO` vienā transakcijā izsniedz eksemplāru un izpilda rindas pirmo rezervāciju. Atsevišķa izpildīšana bez izsnieguma tiek bloķēta. |
| Paziņojums par aktīvu rezervāciju nebija informatīvs | Rezervācijas paziņojumu rāda tad, ja rindas pirmajam lasītājam jau ir pieejams brīvs eksemplārs; teksts norāda, ka grāmatu var saņemt. Kavētie izsniegumi paliek paziņojumu sarakstā. |
| Nepietiekama demonstrācijas datu kopa | Sākotnējās datubāzes izveidē pievienotas 8 grāmatas dažādās kategorijās, daļai pa 2 eksemplāriem. Ievietošana ir idempotenta. **Esošas datubāzes aizņēmumu termiņi netiek pārrakstīti.** |
| Formu loģika sajaukta ar datu loģiku | Datu integritātes pārbaudes un transakcijas glabājas DAO; kopīga GUI metode attēlo izsniegumu sarakstu. Visa GUI Builder formu pārbūve nav veikta, lai nesabojātu esošo `.form` un `Java Swing` saskarni. |
| ISBN imports | `GramataDAO.fetchBookMetadataByIsbn` jau izmanto Google Books API. Ārējā API darbība prasa interneta pieslēgumu un netika izmantota automātiskajos bezsaistes testos. |
| Dokumenta noformējums | Pielāgoti Times New Roman/Courier New stili, A4 lapas malas, galveno nodaļu virsraksti, lappušu numerācija (bez titullapas), pievienots 77.–78. stundas testu apkopojums Word dokumentā. |
| Divu autoru GitHub ieguldījuma pierādījums | **Nav iespējams rekonstruēt no ZIP**. Nav veidoti viltoti commit ieraksti; katram autoram pašam jāveido un jānosūta savas izmaiņas īstajā repozitorijā. |

## Testu rezultāti

Testi izpildīti **atsevišķās JUnit 4 palaišanās** ar Java 21, Apache Derby un testa pagaidu datubāzēm:

- `DatabaseIntegrationTest`: **12 testi, 0 kļūdu, 0 neveiksmju**.
- `BoundaryAndInteractionTest`: **1 plašs robežgadījumu/integrācijas tests, 0 kļūdu, 0 neveiksmju**. Aptver nederīgu gadu (`1799`, `2101`), derīgu gadu (`1800`, `2100`), nosaukumu 201 simbols, tukšu nosaukumu, eksemplāru skaitu (`-1`, `0`, `2`, `101`), datuma sintaksi un neiespējamu datumu (`2027-02-30`), trūkstošu termiņu, pagātnes un pārāk tālu termiņu, unikālu ISBN, transakcijas atcelšanu, izsniegšanu, rezervāciju rindu, dzēšanas aizliegumu, lasītāju datu izolāciju un atgriešanu.
- `QuickGuiFlowTest`: **2 GUI testi, 0 kļūdu, 0 neveiksmju**. Pieteikšanās, kļūdaina parole, grāmatas/lomas datu attēlošana, rediģēšana, ievades kļūdas, izlogošanās/atkārtota pieteikšanās un lasītāja paša izsniegumu attēlošana.

**Kopā: 15 JUnit testi atsevišķās palaišanās iziet.** Kombinēta GUI un DB testu palaišana vienā JVM šeit nenoslēdzās noteiktajā laikā, tāpēc netiek apgalvots, ka viena kopīga palaišana būtu veiksmīga. GUI testiem nepieciešama grafiskā vide (Linux vidē var izmantot `xvfb-run`).

## Kā pārbaudīt NetBeans

1. Atveriet `build.xml` kā NetBeans Java with Ant projektu un izmantojiet JDK 21.
2. Izvēlieties **Clean and Build** (`ant clean jar`).
3. Palaidiet aplikāciju ar sākumklasi `bibliotekas_informacijas_sistema.LibInfoSaskarne`.
4. Palaidiet katru JUnit testa klasi atsevišķi, piemēram:

   ```bash
   ant compile-test
   java -cp "build/classes:build/test/classes:lib/*" org.junit.runner.JUnitCore bibliotekas_informacijas_sistema.BoundaryAndInteractionTest
   java -cp "build/classes:build/test/classes:lib/*" org.junit.runner.JUnitCore bibliotekas_informacijas_sistema.DatabaseIntegrationTest
   # GUI testiem Linux vidē:
   xvfb-run -a java -cp "build/classes:build/test/classes:lib/*" org.junit.runner.JUnitCore bibliotekas_informacijas_sistema.QuickGuiFlowTest
   ```

5. Demonstrācijas kontu sākotnējā parole: `Test123!` (piemēri `administrators`, `bibliotekars`, `lasitajs`). **Nemainiet reālu lietotāju paroles pēc testēšanas.**
6. Testi izmanto savu pagaidu Derby DB ar `libinfo.db.path`, un nemaina projekta `lib_db`. Ja vajag pārbaudīt demonstrācijas datu ģenerēšanu no jauna, palaidiet lietotni ar atsevišķu tukšu DB ceļu, **neizdzēšot** jau esošo DB.

## Versiju kontrole

Šajā ZIP nav sākotnējās `.git` vēstures. Lai labojumi būtu pārbaudāmi, sākotnējā GitHub projekta lokālajā kopijā ievietojiet labotos failus, pārskatiet atšķirības un izveidojiet commit ar **patieso autoru**. Otrs komandas dalībnieks lai atsevišķā commit iesniedz paša veiktu darbu, piemēram, papildu testus vai dokumentācijas labojumus. Nevajag pārrakstīt vai viltot commit autorību.

**Papildu pārbaudījums:** `LietotajsDAO.markNotificationRead` un tabula `pazinojums_izlasits` saglabā paziņojumu izlasīšanas statusu; `RezervacijaDAO.findReservations` ļauj lasītājam skatīt savas rezervācijas, un `QuickGuiFlowTest` pārbauda lomu pāreju.
