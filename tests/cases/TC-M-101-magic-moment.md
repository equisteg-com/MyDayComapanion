# TC-M-101 Magic moment (manual, device)

1. Fresh install, airplane mode ON.
2. Type: "Tomorrow I need to finish the proposal, call Raj, work out, buy groceries, and I have a meeting at 11."
3. Tap **Plan it**. Expect a proposed plan for tomorrow with the meeting fixed at 11:00–12:00 and all four tasks placed.
4. Tap **Add to my day**. Expect the plan saved and shown under tomorrow's date.
5. Tick "Call Raj", tap **Replan**. Expect remaining tasks re-placed with no overlaps.
6. History tab shows `ai.extract`, `tasks.create`, `plan.generate`, `tasks.complete` with no task titles.

Automated equivalent: `app/src/test/.../MagicMomentUiTest.kt`, `domain/.../AgentOrchestratorTest.kt`.
