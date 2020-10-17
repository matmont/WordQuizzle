package back_end_src;

/**
 * {@link ScoreStruct} ha il compito di supportare {@link MatchManager} nella gestione
 * delle statistiche partita dei vari {@link User}. Non c'è bisogno di commentare ogni metodo,
 * sono semplicemente dei getter/setter/modifier dei vari valori.
 */
public class ScoreStruct {
    private int pointsA;
    private int guessedA;
    private int wrongsA;
    private int noneA;

    private int pointsB;
    private int guessedB;
    private int wrongsB;
    private int noneB;

    public ScoreStruct() {
        this.pointsA = 0;
        this.guessedA = 0;
        this.wrongsA = 0;
        this.noneA = Server.numberOfWords;

        this.pointsB = 0;
        this.guessedB = 0;
        this.wrongsB = 0;
        this.noneB = Server.numberOfWords;
    }

    public void setPointsA(int pointsA) {
        this.pointsA = pointsA;
    }

    public int getPointsA() {
        return pointsA;
    }

    public void setPointsB(int pointsB) {
        this.pointsB = pointsB;
    }

    public int getPointsB() {
        return pointsB;
    }

    public int getGuessedA() {
        return guessedA;
    }

    public int getGuessedB() {
        return guessedB;
    }

    public int getWrongsA() {
        return wrongsA;
    }

    public int getWrongsB() {
        return wrongsB;
    }

    public int getNoneA() {
        return noneA;
    }

    public int getNoneB() {
        return noneB;
    }

    public void incrementPointsA(int increment) {
        pointsA += increment;
    }

    public void incrementPointsB(int increment) {
        pointsB += increment;
    }

    public void decrementPointsA(int decrement) {
        pointsA -= decrement;
    }

    public void decrementPointsB(int decrement) {
        pointsB -= decrement;
    }

    public void incrementGuessedA() {
        guessedA++;
    }

    public void incrementGuessedB() {
        guessedB++;
    }

    public void incrementWrongsA() {
        wrongsA++;
    }

    public void incrementWrongsB() {
        wrongsB++;
    }

    public void decrementNoneA() {
        noneA--;
    }

    public void decrementNoneB() {
        noneB--;
    }

    @Override
    public String toString() {
        return "ScoreStruct{" +
                "pointsA=" + pointsA +
                ", guessedA=" + guessedA +
                ", wrongsA=" + wrongsA +
                ", noneA=" + noneA +
                ", pointsB=" + pointsB +
                ", guessedB=" + guessedB +
                ", wrongsB=" + wrongsB +
                ", noneB=" + noneB +
                '}';
    }
}
