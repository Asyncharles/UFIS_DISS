package com.ufis.simulator;

public final class SimulatorHarness {
    private SimulatorHarness() {}

    public static void main(String[] args) {
        DataSimulator.Tier tier = args.length == 0
                ? DataSimulator.Tier.SMALL
                : DataSimulator.Tier.valueOf(args[0].trim().toUpperCase());

        DataSimulator simulator = new DataSimulator();
        DataSimulator.GeneratedDataset dataset = simulator.generate(tier);

        System.out.println("Tier: " + dataset.tier());
        System.out.println("Seed: " + dataset.config().seed());
        System.out.println("Initial entities: " + dataset.initialEntities().size());
        System.out.println("Initial securities: " + dataset.initialSecurities().size());
        System.out.println("Corporate actions: " + dataset.corporateActions().size());
        System.out.println("Action counts: " + dataset.actionCounts());
        System.out.println("Edge cases: " + dataset.edgeCases());
    }
}
