import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap; // Used to sort output

public class KuhnCFR {

    public static final int PASS = 0;
    public static final int BET = 1;

    class Node {
        String infoSet;
        double[] regretSum = new double[2];
        double[] strategySum = new double[2];

        public Node(String infoSet) {
            this.infoSet = infoSet;
        }

        // Calculates current iteration strategy
        public double[] getStrategy(double realizationWeight) {
            double[] strategy = new double[2];
            double normalizingSum = 0;

            for (int i = 0; i < 2; i++) {
                strategy[i] = Math.max(0.0, regretSum[i]);
                normalizingSum += strategy[i];
            }

            for (int i = 0; i < 2; i++) {
                if (normalizingSum > 0) {
                    strategy[i] /= normalizingSum;
                } else {
                    strategy[i] = 0.5;
                }
                strategySum[i] += realizationWeight * strategy[i];
            }
            return strategy;
        }

        // Returns the final Nash Equilibrium strategy
        public double[] getAverageStrategy() {
            double[] avgStrategy = new double[2];
            double normalizingSum = 0;

            for (int i = 0; i < 2; i++) {
                normalizingSum += strategySum[i];
            }
            for (int i = 0; i < 2; i++) {
                if (normalizingSum > 0) {
                    avgStrategy[i] = strategySum[i] / normalizingSum;
                } else {
                    avgStrategy[i] = 0.5;
                }
            }
            return avgStrategy;
        }
    }

    // Hash map to store the game tree nodes
    Map<String, Node> nodeMap = new HashMap<>();

    public Node getNode(String infoSet) {
        return nodeMap.computeIfAbsent(infoSet, Node::new);
    }

    //Training loop
    public void train(int iterations) {
        int[] cards = {1, 2, 3}; // 1 = Jack, 2 = Queen, 3 = King
        Random random = new Random();
        double expectedGameValue = 0.0;

        for (int i = 0; i < iterations; i++) {
            // Shuffle the 3-card deck
            for (int c = cards.length - 1; c > 0; c--) {
                int j = random.nextInt(c + 1);
                int temp = cards[c];
                cards[c] = cards[j];
                cards[j] = temp;
            }

            // Start the recursive tree traversal
            expectedGameValue += cfr(cards, "", 1.0, 1.0);
        }

        System.out.printf("Training complete after %,d iterations.%n", iterations);
        System.out.printf("Expected Value for Player 1: %.6f chips per hand (Expected: ~ -0.0556)%n%n", (expectedGameValue / iterations));
    }

    //Recursive optimization
    public double cfr(int[] cards, String history, double p0, double p1) {
        int plays = history.length();
        int player = plays % 2;
        int opponent = 1 - player;

        // Check for terminal states (Payouts)
        if (plays > 1) {
            boolean terminalPass = history.charAt(plays - 1) == 'p';
            boolean doubleBet = history.endsWith("bb");
            boolean isPlayerCardHigher = cards[player] > cards[opponent];

            if (terminalPass) {
                if (history.equals("pp")) {
                    return isPlayerCardHigher ? 1.0 : -1.0; // Showdown, no bets
                } else {
                    return 1.0; // Unchallenged win (opponent folded)
                }
            } else if (doubleBet) {
                return isPlayerCardHigher ? 2.0 : -2.0; // Showdown, with bets
            }
        }

        String infoSet = cards[player] + history;
        Node node = getNode(infoSet);

        double realizationWeight = (player == 0) ? p0 : p1;
        double[] strategy = node.getStrategy(realizationWeight);

        double[] util = new double[2];
        double nodeUtil = 0.0;

        // Traverse both action branches: PASS (0) and BET (1)
        for (int a = 0; a < 2; a++) {
            String nextHistory = history + (a == 0 ? "p" : "b");
            double nextP0 = (player == 0) ? p0 * strategy[a] : p0;
            double nextP1 = (player == 1) ? p1 * strategy[a] : p1;

            util[a] = -cfr(cards, nextHistory, nextP0, nextP1);
            nodeUtil += strategy[a] * util[a];
        }

        // Calculate and accumulate counterfactual regret
        double opponentWeight = (player == 0) ? p1 : p0;
        for (int a = 0; a < 2; a++) {
            double regret = util[a] - nodeUtil;
            node.regretSum[a] += opponentWeight * regret;
        }

        return nodeUtil;
    }

    public static void main(String[] args) {
        KuhnCFR trainer = new KuhnCFR();

        // Run 1 million iterations
        trainer.train(1_000_000);

        System.out.println("NASH EQUILIBRIUM STRATEGIES (Card + History):");
        System.out.println("---------------------------------------------");

        // Sort the output for readability using a TreeMap
        Map<String, Node> sortedNodes = new TreeMap<>(trainer.nodeMap);

        for (Node node : sortedNodes.values()) {
            double[] strategy = node.getAverageStrategy();

            // Format card name for display
            String cardName = node.infoSet.startsWith("1") ? "Jack " :
                    node.infoSet.startsWith("2") ? "Queen" : "King ";
            String history = node.infoSet.substring(1);
            if (history.isEmpty()) history = "Start";

            System.out.printf("[%s] History: %-5s -> PASS: %5.1f%% | BET: %5.1f%%%n",
                    cardName, history, strategy[0] * 100, strategy[1] * 100);
        }
    }
}