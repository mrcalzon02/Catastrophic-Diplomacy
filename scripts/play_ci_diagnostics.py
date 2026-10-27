#!/usr/bin/env python3
"""Convert actual Google Play CI failures into reasons and corrective actions."""
import json, os, re, sys
from pathlib import Path

HELP = [
(r"Missing class|R8|Could not resolve|Execution failed for task",
 "Dependency/build failure", "Inspect Gradle releaseRuntimeClasspath and the named task; provide the real missing dependency or repair failing source, then rebuild. Never suppress missing classes."),
(r"ELF|16 KB|zip align|zip alignment",
 "16 KB page compatibility", "Rebuild affected native libraries for 16 KB ELF PT_LOAD page alignment and verify the APK with Android SDK zipalign -P 16; rerun APK and AAB tests."),
(r"REQUEST_INSTALL_PACKAGES|game updater|external game installer|sideload",
 "External installation/update policy", "Remove independent APK install/update mechanisms from Play builds, including UI, merged manifest components and install permissions; distribute through Google Play."),
(r"targetSdk|target SDK|compileSdk",
 "Target SDK", "Upgrade Play-specific Gradle target/compile SDK to the currently required Android API and verify the compiled APK."),
(r"undeclared merged permissions|restricted permissions",
 "Permission audit", "Inspect aapt dump permissions and merged manifest. Remove unexpected SDK permissions or document a permitted use and required Play Console declarations."),
(r"privacy_policy_url|HTTPS privacy-policy",
 "Privacy policy", "Publish a public app-specific HTTPS privacy policy describing data practices, retention, removal and third parties. Enter its actual URL in policy-review.json and Play Console."),
(r"data_safety_evidence", "Data safety", "Audit runtime and third-party SDK data collection/sharing; complete the actual Play Console Data safety form and record submitted evidence."),
(r"content_rating_evidence", "Content rating", "Complete the Play Console IARC questionnaire accurately, including mature content and simulated gambling; save the resulting rating evidence."),
(r"target_audience_evidence", "Target audience", "Declare correct target age groups and Families/advertising applicability in Play Console; document the submission."),
(r"store_listing_evidence", "Store listing", "Verify images, screenshots, claims, copyright, title and gameplay against the submitted build; document Play Console listing."),
(r"sdk_inventory_evidence", "SDK inventory", "Inventory bundled dependencies, SDK permissions, data transfers and licenses; document library versions and SDK policy findings."),
(r"Reviewed policy: ", "Individual policy review", "Open the named policy in the official Google Play Developer Policy Center; record concrete compliance or an explained non-applicable decision with dated evidence and reviewer in docs/google-play/policy-review.json."),
(r"Play APK|Play AAB|APK integrity|Bundle", "Play artifact", "Repair the actual assemblePlay/bundlePlay Gradle build. Inspect and rerun tests against both generated APK and AAB."),
(r"signature|Signing", "Signing", "Repair the CI-only certificate validation; provision a separate permanent Play upload key for production, never the ephemeral CI key."),
]

def remedy(s):
    for pat, topic, action in HELP:
        if re.search(pat, s, re.I):
            return topic, action
    return "Other failed assertion", "Inspect its saved phase log; correct the named source or declaration and re-run CI. Never waive release failures."

def main():
    if "--self-test" in sys.argv:
        for sample in ("Reviewed policy: Ads", "Missing class javax.el.BeanELResolver",
                       "16 KB native ELF", "data_safety_evidence"):
            assert remedy(sample)[0] != "Other failed assertion"
        print("PASS: failure categorization and solution-vector self-tests")
        return 0
    folder = Path(os.getenv("RUNNER_TEMP", "/tmp")) / "play-ci"
    folder.mkdir(parents=True, exist_ok=True)
    states = {name: os.getenv("PLAY_" + name.upper() + "_OUTCOME", "missing")
              for name in ("source", "build", "artifact", "review")}
    findings = []
    for phase, state in states.items():
        f = folder / (phase + ".log")
        lines = f.read_text(errors="replace").splitlines() if f.exists() else []
        messages = [re.sub(r"\x1b\[[0-9;]*m", "", line)[6:]
                    for line in lines if line.startswith("FAIL: ")]
        if phase == "build" and state == "failure":
            messages += [line.strip()[-500:] for line in lines
                         if "Missing class" in line or "Execution failed for task" in line or "BUILD FAILED" in line]
        if state == "failure" and not messages:
            messages = ["Failed phase has no recognized assertion; inspect " + phase + ".log"]
        for message in dict.fromkeys(messages):
            topic, action = remedy(message)
            findings.append({"phase": phase, "reason": message,
                             "category": topic, "solution": action,
                             "policy_url": "https://play.google/developer-content-policy/"})
    blocked = any(v != "success" for v in states.values())
    report = {"schema": 1, "revision": os.getenv("GITHUB_SHA"), "outcomes": states,
              "release_eligible": not blocked, "findings": findings,
              "note": "CI tests cannot certify Play approval or replace human review."}
    (folder / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    summary = ["## Google Play release eligibility", "",
               "**BLOCKED**" if blocked else "**Configured tests passed — not Play approval**",
               "", "| Phase | Actual result |", "|---|---|"]
    summary += ["| " + k + " | " + v + " |" for k, v in states.items()]
    summary += ["", "### Failure reasons and correction vectors", ""]
    for idx, row in enumerate(findings, 1):
        summary += [str(idx) + ". **" + row["category"] + "** (" + row["phase"] + ")",
                    "   - Failure: " + row["reason"],
                    "   - Correction: " + row["solution"],
                    "   - Policy: " + row["policy_url"]]
    if states["build"] != "success":
        summary += ["", "Build was unsuccessful; artifact compliance is NOT established."]
    summary += ["", "Full structured findings are in play-ci/report.json.", ""]
    text = "\n".join(summary)
    if os.getenv("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as fp:
            fp.write(text)
    print(text)
    for row in findings[:12]:
        message = (row["reason"] + " | FIX: " + row["solution"])[:1400]
        message = message.replace("%", "%25").replace("\n", " ").replace("\r", "%0D")
        print("::error title=" + row["category"] + "::" + message)
    return 1 if blocked else 0

if __name__ == "__main__":
    sys.exit(main())
