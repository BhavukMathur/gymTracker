package com.gymtracker.service;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

/**
 * Serves static wellness tips for the /api/health-tips API. No medical advice;
 * general habit and movement ideas only.
 */
@Service
public class HealthTipService {

    private static final List<String> TIPS = List.of(
            "Drink water before you feel thirsty — a glass at breakfast is an easy start.",
            "A five-minute walk after a meal can help you feel more settled than sitting right away.",
            "Sleep sets the table for energy: a consistent wake time often matters more than a perfect bedtime.",
            "Warm up joints with easy movements before heavier lifts or intense cardio.",
            "Pair a new habit with a fixed anchor (e.g. right after you brush your teeth).",
            "Protein with each main meal can make afternoon cravings less dramatic.",
            "If you're sore, light movement often beats full bed rest (unless a clinician says otherwise).",
            "Breathe in through the nose for easy cardio when you can; it can feel steadier.",
            "Dim screens in the last hour before sleep if you want calmer wind-downs.",
            "Add vegetables to the plate you already eat instead of inventing a new diet from scratch.",
            "Track consistency, not perfection — a partial workout beats skipping entirely.",
            "Step outside for a few minutes of daylight early in the day if your schedule allows.",
            "Stretch the muscle groups you actually trained; mirror your session when you can.",
            "Eating slowly gives your body time to register fullness before seconds.",
            "If motivation is low, commit to ten minutes; you can always stop, but often you won't need to.",
            "Set out workout clothes the night before if mornings feel rushed.",
            "A cool-down and a few deep breaths can make the day after a hard session feel better.",
            "Fruit in plain sight (bowl on the counter) nudges better snacking more than willpower alone.",
            "For desk days, stand or pace for one minute on the hour; small breaks add up.",
            "Caffeine late in the day can nibble at sleep; try a cutoff a few hours before bed.",
            "Hunger can masquerade as low energy; a balanced snack is sometimes what you need, not willpower.",
            "Keep rest days in the week on purpose; recovery is part of the plan, not a failure.",
            "When learning a lift, form beats weight — a lighter set you own beats a shaky rep.",
            "A short list of 3 to 5 go-to healthy meals can reduce 'what’s for dinner' stress mid-week.",
            "Loud music can make cardio feel easier; save the energy for the last few minutes if you use it.",
            "Swap one sugary drink a day for water, tea, or seltzer — a small change that scales.",
            "Laying out one healthy ingredient when you get home makes a better dinner more likely that night.",
            "If the gym is crowded, have a back-up plan (e.g. bodyweight or a different machine) so you do not leave.",
            "A gratitude or ‘done list’ for the day, even one line, can ease stress that feeds late snacking.",
            "Walking meetings or phone calls add steps without a separate 'exercise' block in your calendar.",
            "A stable gym bag in the car or at work cuts excuses on busy days.",
            "Fiber-rich foods (beans, oats, produce) with meals can make energy feel more even all afternoon.",
            "Cool air or a short shower after training can help you switch from workout mode to the rest of the day."
    );

    public String randomTip() {
        int i = ThreadLocalRandom.current().nextInt(TIPS.size());
        return TIPS.get(i);
    }
}
