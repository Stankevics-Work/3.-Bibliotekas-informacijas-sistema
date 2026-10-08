# 3.-Bibliotekas-informacijas-sistema - A. Stankevičs (StankevicsWork) un I. Aņismovs (streightlesson-spec)

Esošais NetBeans Java with Ant projekts papildināts atbilstoši dokumenta modeļiem un DAO
Projekta nosaukums, Ant struktūra, galvenā klase un Java 21 iestatījumi saglabāti

## Palaišana

1. Izpako arhīvu jaunā mapē
2. NetBeans izvēlies File → Open Project un atver 3.-Bibliotekas-informacijas-sistema
3. Properties → Libraries → Java Platform izvēlies JDK 21 vai jaunāku
4. Izpildi Clean and Build, pēc tam Run Project
5. Konsolē parādās JDBC savienojuma pārbaude un inicializācijas rezultāts
6. Test Project palaiž četrus JUnit4 integrācijas testus

Bibliotēkas atrodas lib mapē un ir piesaistītas ar relatīviem ceļiem
Nav vajadzīgs Maven vai atsevišķa Derby servera palaišana
Ja projektā redzi platformas kļūdu, izvēlies instalēto JDK projekta Properties → Libraries sadaļā
Windows alternatīva: run.bat un test.bat, vajadzīgs JDK 21 un javac PATH mainīgajā

## Klases

Pamatpakotne: pkg3.bibliotekas.informacijas.sistema

- BibliotekasInformacijasSistema — esošā galvenā klase ar inicializācijas un JDBC pārbaudes izsaukumiem
- db.DatabaseManager — savienojums, shēmas inicializācija un JDBC pārbaude
- model.Lietotajs, Gramata, Eksemplars, Izsniegums, Rezervacija un Loma
- dao.LietotajsDAO, GramataDAO, EksemplarsDAO, IzsniegumsDAO un RezervacijaDAO
- util.PasswordUtil — BCrypt
- src/schema.sql — piecas tabulas, skati un trigeri
- test — JUnit4 integrācijas testi

Modeļu lauki un objektu saites atbilst dokumentam
Grāmatu pieejamību automātiski atjaunina trigeri
DAO nodrošina pievienošanu, meklēšanu pēc ID, sarakstus, rediģēšanu un dzēšanu
Papildus ir grāmatu meklēšana un filtrēšana, aktīvie un kavētie izsniegumi un rezervāciju rinda

## Datubāze

Darba datubāze automātiski izveidojas data/LibInfo mapē
Esošās lib_db un sample datubāzes saglabātas oriģinālajā formā un netiek mainītas
lib_db pieprasa autentifikāciju, tās piekļuves dati arhīvā nav norādīti
Tāpēc šīs versijas noklusējuma palaišana izmanto jauno data/LibInfo datubāzi
Atkārtota inicializācija nedzēš datus
Ceļu var mainīt ar JVM parametru -Dlibinfo.db.path=C:/LibInfo/data
Esošu shēmu automātiska migrācija nav paredzēta

## DAO piemērs

```java
try (Connection c = DatabaseManager.getConnection()) {
    DatabaseManager.initialize(c);
    GramataDAO dao = new GramataDAO(c);
    List<Gramata> gramatas = dao.search("Java");
}
```

Importi: java.sql.Connection, java.util.List, pkg3.bibliotekas.informacijas.sistema.db.DatabaseManager, pkg3.bibliotekas.informacijas.sistema.dao.GramataDAO un pkg3.bibliotekas.informacijas.sistema.model.Gramata
Izsaucējs aizver savienojumu, katram darba pavedienam vajadzīgs savs savienojums
No Swing izmanto SwingWorker, lai datubāzes darbības neapturētu logu

insert atgriež jauno ID, findById atgriež null, ja ieraksts nav atrasts
update un delete atgriež true, ja mainīta viena rinda
IzsniegumsDAO.insert un RezervacijaDAO.insert pārvalda savu SERIALIZABLE transakciju un sagaida autoCommit=true
Izsniegšana pārbauda eksemplāra pieejamību un rezervāciju rindu
IzsniegumsDAO.atgriezt(id) saglabā atgriešanas datumu un atbrīvo eksemplāru
IzsniegumsDAO.update maina tikai termiņu, EksemplarsDAO.update maina tikai bibliotēku
RezervacijaDAO.update atceļ aktīvu rezervāciju, izpildi reģistrē izsniegšanas metode
Neatgrieztus izsniegumus un aktīvas rezervācijas dzēst nevar
Ārējās atslēgas aizsargā saistītos datus
LietotajsDAO sagaida BCrypt jaucējvērtību, to ģenerē PasswordUtil.hash(parole)
Darbinieku tiesības, pieteikšanās sesijas un Swing logi vēl jārealizē servisa un GUI slānī

## Pārbaude

Pielāgotā projekta klases kompilētas un palaistas ar OpenJDK 17 Java 8 saderības režīmā
JDBC savienojums un četri JUnit4 integrācijas testi pārbaudīti ar īstu Derby 10.14.2.0
Oriģinālais NetBeans Java 21 iestatījums saglabāts, NetBeans interfeisa un Ant būvēšana šajā vidē nav pārbaudīta

Testi pārbauda CRUD, BCrypt, datu saglabāšanu pēc Derby restartēšanas, izdoto eksemplāru aizsardzību, atgriešanu, rezervāciju rindu, kavējumus un datu ierobežojumus

Git vēsture saglabāta, attālinātais repozitorijs nav mainīts
Arhīvā nav vecās build/dist izvades vai cita datora nbproject/private iestatījumu
