package ppg.atoms;

import java.util.*;

public class SymbolList {
    public static final int TERMINAL = 0;
    public static final int NONTERMINAL = 1;

    private int variety;
    private String type;
    private Vector<String> symbols;

    public SymbolList(int which, String type, Vector<String> syms) {
        variety = which;
        this.type = type;
        symbols = syms;
    }

    public boolean dropSymbol(String gs) {
        for (int i = 0; i < symbols.size(); i++) {
            if (gs.equals(symbols.elementAt(i))) {
                symbols.removeElementAt(i);
                // assume we do not have duplicates
                return true;
            }
        }
        return false;
    }

    @Override
    public Object clone() {
        String newType = (type == null) ? null : type.toString();
        Vector<String> newSyms = new Vector<String>();
        for (int i = 0; i < symbols.size(); i++) {
            newSyms.addElement(symbols.elementAt(i).toString());
        }
        return new SymbolList(variety, newType, newSyms);
    }

    @Override
    public String toString() {
        String result = "";

        if (symbols.size() > 0) {
            switch (variety) {
            case (TERMINAL):
                result = "terminal ";
                break;
            case (NONTERMINAL):
                result = "non terminal ";
                break;
            }

            if (type != null) result += type + " ";

            int size = symbols.size();
            for (int i = 0; i < size; i++) {
                result += symbols.elementAt(i);
                if (i < size - 1) result += ", ";
            }
            result += ";";
        }

        return result;
    }
}
