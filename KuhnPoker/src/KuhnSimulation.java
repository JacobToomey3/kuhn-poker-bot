import java.util.Random;

public class KuhnSimulation {

    public static void main(String[] args) {
        System.out.println("1. Training GTO Baseline...");
        KuhnCFR gto = new KuhnCFR();
        gto.train(1_000_000); // Runs your previous CFR algorithm

        System.out.println("\n2. Initializing Exploitative Bot...");
        OpponentModel model = new OpponentModel();
        ExploitEngine bot = new ExploitEngine(model, gto);

        int botIndex = 0; // For this test, the bot always acts first (Player 1)
        int botProfits = 0;
        int handsToPlay = 10_000;
        Random random = new Random();

        System.out.println("\n3. Starting Simulation vs. Risk-Averse Human (The Nit)");
        System.out.println("-------------------------------------------------------");

        for (int i = 1; i <= handsToPlay; i++) {
            int[] cards = shuffleDeck(random);
            String history = "";

            // Play out the hand node by node
            while (!isTerminal(history)) {
                int currentPlayer = history.length() % 2;
                int action;

                if (currentPlayer == botIndex) {
                    // Bot uses the Expectimax Exploit Engine
                    action = bot.getBestAction(cards, history, botIndex);
                } else {
                    // Human uses hardcoded flawed logic
                    action = getFlawedHumanAction(cards[currentPlayer], history);

                    // The Bayesian Tracker observes the human's behavior.
                    // (Note: In a live game, we only see their card at showdown. 
                    // For this basic prototype, we grant perfect hindsight to train the model).
                    String infoSet = cards[currentPlayer] + history;
                    model.observeAction(infoSet, action);
                }

                history += (action == KuhnCFR.BET) ? "b" : "p";
            }

            // Calculate chips won/lost
            botProfits += calculatePayout(cards, history, botIndex);

            // Log progress
            if (i % 2500 == 0) {
                System.out.printf("Hands Played: %-6d | Bot Profit: %-5d chips | Win Rate: %+.3f chips/hand%n",
                        i, botProfits, (double) botProfits / i);
            }
        }
    }

    /**
     * Flawed player ("The Nit")
     * Microeconomic profile: Extreme risk aversion. 
     * Logic: Only bets or calls with a King (3). Folds everything else.
     */
    private static int getFlawedHumanAction(int card, String history) {
        if (card == 3) return KuhnCFR.BET;
        return KuhnCFR.PASS;
    }

    // --- HELPER FUNCTIONS --- //

    private static int[] shuffleDeck(Random random) {
        int[] cards = {1, 2, 3};
        for (int c = 2; c > 0; c--) {
            int j = random.nextInt(c + 1);
            int temp = cards[c];
            cards[c] = cards[j];
            cards[j] = temp;
        }
        return cards;
    }

    private static boolean isTerminal(String history) {
        int plays = history.length();
        if (plays <= 1) return false;
        return history.charAt(plays - 1) == 'p' || history.endsWith("bb");
    }

    private static int calculatePayout(int[] cards, String history, int botIndex) {
        int opponent = 1 - botIndex;
        boolean botHasHigherCard = cards[botIndex] > cards[opponent];

        if (history.charAt(history.length() - 1) == 'p') {
            if (history.equals("pp")) return botHasHigherCard ? 1 : -1;
            // The last action was a pass after a bet (a fold).
            // If the history length is even, Player 2 folded (Bot wins).
            return (history.length() % 2 == 0) ? 1 : -1;
        }
        // Double bet showdown ("bb" or "pbb")
        return botHasHigherCard ? 2 : -2;
    }
}