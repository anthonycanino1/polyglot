package ppg.atoms;

public class Nonterminal extends GrammarSymbol {
    public Nonterminal(String name, String label) {
        this.name = name;
        this.label = label;
    }

    public Nonterminal(String name) {
        this.name = name;
        label = null;
    }

    @Override
    public Object clone() {
        return new Nonterminal(name, label);
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Nonterminal) {
            return name.equals(((Nonterminal) o).getName());
        }
        else if (o instanceof String) {
            // do we even need the nonterminal/terminal distinction?
            return name.equals(o);
        }
        return false;
    }
}
