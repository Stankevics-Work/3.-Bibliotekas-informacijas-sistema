package pkg3.bibliotekas.informacijas.sistema.model;

/** Lietotajs datu modelis atbilstoši projekta dokumentam */
public class Lietotajs {
    private int lietotajaId;
    private String vards;
    private String uzvards;
    private String lietotajvards;
    private String parole;
    private String epasts;
    private Loma loma;

    public Lietotajs() { }

    public int getLietotajaId() { return lietotajaId; }
    public void setLietotajaId(int lietotajaId) { this.lietotajaId = lietotajaId; }

    public String getVards() { return vards; }
    public void setVards(String vards) { this.vards = vards; }

    public String getUzvards() { return uzvards; }
    public void setUzvards(String uzvards) { this.uzvards = uzvards; }

    public String getLietotajvards() { return lietotajvards; }
    public void setLietotajvards(String lietotajvards) { this.lietotajvards = lietotajvards; }

    public String getParole() { return parole; }
    public void setParole(String parole) { this.parole = parole; }

    public String getEpasts() { return epasts; }
    public void setEpasts(String epasts) { this.epasts = epasts; }

    public Loma getLoma() { return loma; }
    public void setLoma(Loma loma) { this.loma = loma; }

    @Override public String toString() { return vards + " " + uzvards + " (" + lietotajvards + ")"; }
}
