#!/usr/bin/env python3
"""
Builds an OpenBubbles-format export full of conversations that never happened.

The app can already import one of these, so this is the shortest path to a
database with enough in it to judge scrolling, paging and the conversation
list against - and unlike the built-in sample threads it can be as large as
you like without shipping that size inside the APK.

The format is what OpenBubblesImport reads: a four-byte big-endian length,
that many bytes of JSON, then one block per attachment that claimed a
`bytes_id`. Nothing here claims one, so the file ends at the JSON and the
attachments import as the references they would have been before download.

    python3 tools/make_test_export.py out.obbackup --chats 30 --messages 300
"""

import argparse
import json
import random
import struct
import time

OPENERS = [
    "hey are you around", "ok so", "quick one", "did you see this",
    "morning", "you free later", "update:", "so it turns out",
]
LINES = [
    "yeah that works", "one sec", "on my way", "haha", "no way",
    "I'll be there in 10", "can you send me the address",
    "just got out of a meeting", "sounds good to me",
    "wait what?!", "hmm let me check", "ok done", "perfect thanks",
    "did you eat yet", "I'm so tired today", "that's hilarious",
    "let me know when you're free", "same", "I'll call you after",
    "sorry just seeing this", "yes please", "not sure yet",
    "it's pouring out here", "running about 15 late",
    "you were right about that place", "ok booking it now",
    "I completely forgot", "next week works better for me",
    "STOP!", "are you serious right now", "ok that's actually amazing",
    "sending it over in a sec", "did that go through?",
    "all set on my end", "let's do tuesday", "works for me",
]
LONG = [
    "ok so the whole thing turned out to be a misunderstanding about the "
    "booking, they had us down for the wrong night and then tried to tell me "
    "the confirmation email didn't exist. anyway it's sorted now but I'm "
    "never going back there",
    "I've been thinking about what you said and I think you're right, it "
    "doesn't make sense to keep paying for something we use maybe twice a "
    "month. let's cancel it and see if we miss it",
    "quick recap since you were out: the deadline moved up a week, the scope "
    "is the same, and they want a draft by friday. I said that was tight but "
    "doable if we drop the last section",
]
FIRST = [
    "Ava", "Marco", "Priya", "Jordan", "Kai", "Sam", "Nina", "Diego",
    "Ruth", "Theo", "Maya", "Otis", "Lena", "Hugo", "Iris", "Felix",
    "Nadia", "Caleb", "Rosa", "Emil", "Tess", "Aziz", "June", "Milo",
]
LAST = [
    "Chen", "Diaz", "Raman", "Blake", "Okafor", "Whitfield", "Costa",
    "Novak", "Haddad", "Lindqvist", "Moreau", "Ferreira", "Abara", "Yun",
]
GROUP_NAMES = [
    "Taco Crew", "Weekend Plans", "Flat 3B", "Book Club", "Sunday League",
    "Road Trip", "The Group Chat", "Work Overflow", "Cabin Weekend",
]
EFFECTS = [
    "", "", "", "", "",
    "com.apple.MobileSMS.expressivesend.impact",
    "com.apple.MobileSMS.expressivesend.loud",
    "com.apple.MobileSMS.expressivesend.gentle",
]
TAPBACKS = ["love", "like", "laugh", "emphasize", "question"]

# What the first conversation is made to contain, by position, so every branch
# of the importer is exercised no matter how small the file is asked to be.
FORCED = {
    1: "attachment",
    2: "reaction",
    3: "reply",
    4: "edit",
    5: "unsend",
}


def build(chat_count, per_chat, seed):
    rng = random.Random(seed)
    now = int(time.time() * 1000)
    day = 86_400_000

    chats, messages, atts = [], [], []
    used_names = set()

    def person(index):
        while True:
            name = f"{rng.choice(FIRST)} {rng.choice(LAST)}"
            if name not in used_names:
                used_names.add(name)
                return {"name": name, "address": f"+1555{index:07d}"}

    people = [person(i) for i in range(chat_count * 3)]

    for c in range(chat_count):
        group = rng.random() < 0.28
        members = (
            rng.sample(people, rng.randint(2, 4)) if group else [people[c]]
        )
        guid = (
            f"iMessage;+;chat{rng.getrandbits(48):012x}"
            if group
            else f"iMessage;-;{members[0]['address']}"
        )
        chats.append({
            "guid": guid,
            "displayName": rng.choice(GROUP_NAMES) if group else "",
            "participants": [{"address": m["address"]} for m in members],
            "isPinned": c < 3,
            "isArchived": c >= chat_count - 2,
            "muteType": "mute" if c % 11 == 5 else "",
        })

        # Conversations are spread across the year and bunched into bursts, so
        # the transcript has date breaks in it rather than one even drizzle -
        # which is what actually exercises the headers and the paging.
        cursor = now - rng.randint(2 * day, 300 * day)
        count = rng.randint(int(per_chat * 0.5), int(per_chat * 1.5))
        # One thread far longer than the rest. The store reads a conversation
        # a page at a time and paints a smaller first screenful still, and
        # neither of those limits means anything until something crosses them.
        if c == 0:
            count *= 5
        recent = []

        for i in range(count):
            gap = rng.choice([
                rng.randint(20_000, 400_000),
                rng.randint(20_000, 400_000),
                rng.randint(2 * 3_600_000, 30 * 3_600_000),
            ])
            cursor = min(cursor + gap, now - 60_000)
            from_me = rng.random() < 0.48
            who = None if from_me else rng.choice(members)
            guid_m = f"msg-{c}-{i}-{rng.getrandbits(32):08x}"

            roll = rng.random()
            if i == 0:
                text = rng.choice(OPENERS)
            elif roll < 0.06:
                text = rng.choice(LONG)
            else:
                text = rng.choice(LINES)

            row = {
                "guid": guid_m,
                "chat": guid,
                "text": text,
                "dateCreated": cursor,
                "isFromMe": from_me,
                "sendingServiceId": "iMessage",
                "expressiveSendStyleId": rng.choice(EFFECTS),
            }
            if not from_me:
                row["handle"] = {"address": who["address"]}
                row["dateDelivered"] = cursor
            else:
                row["dateDelivered"] = cursor + 1200
                if rng.random() < 0.7:
                    row["dateRead"] = cursor + rng.randint(5_000, 900_000)

            # A reply, pointing at something recent rather than anything at
            # all - a thread reaching back two hundred messages is not a
            # thread anybody has ever had.
            if recent and rng.random() < 0.09:
                row["threadOriginatorGuid"] = "p:0/" + rng.choice(recent[-12:])

            # The first conversation carries one of everything, whatever the
            # dice say. This file is also the test fixture, and a fixture
            # whose coverage depends on a seed is one that starts passing by
            # luck and fails months later for a reason nobody can find.
            forced = c == 0 and i in FORCED

            if forced and FORCED[i] == "edit":
                row["dateEdited"] = cursor + 60_000
            elif forced and FORCED[i] == "unsend":
                row["dateDeleted"] = cursor + 90_000
            elif forced and FORCED[i] == "reply" and recent:
                row["threadOriginatorGuid"] = "p:0/" + recent[-1]
            elif rng.random() < 0.04:
                row["dateEdited"] = cursor + 60_000
            elif rng.random() < 0.02:
                row["dateDeleted"] = cursor + 90_000

            if (forced and FORCED[i] == "attachment") or rng.random() < 0.035:
                att_guid = f"att-{c}-{i}"
                row["attachments"] = [{
                    "guid": att_guid,
                    "transferName": rng.choice([
                        "IMG_4821.HEIC", "screenshot.png", "receipt.pdf",
                        "clip.mov", "voice-memo.caf",
                    ]),
                    "mimeType": rng.choice([
                        "image/heic", "image/png", "application/pdf",
                        "video/quicktime", "audio/x-caf",
                    ]),
                    "totalBytes": rng.randint(40_000, 8_000_000),
                }]
                # No bytes_id: the bytes are not in this file, so these import
                # as references, which is what an undownloaded attachment is.
                atts.append({"guid": att_guid})

            messages.append(row)
            recent.append(guid_m)

            # Reactions are their own rows in this format, pointing back at
            # the message they landed on.
            if (forced and FORCED[i] == "reaction") or rng.random() < 0.08:
                reactor = None if not from_me else rng.choice(members)
                react = {
                    "guid": f"tb-{c}-{i}",
                    "chat": guid,
                    "text": "",
                    "dateCreated": cursor + 30_000,
                    "isFromMe": reactor is None,
                    "associatedMessageGuid": "p:0/" + guid_m,
                    "associatedMessageType": rng.choice(TAPBACKS),
                }
                if reactor is not None:
                    react["handle"] = {"address": reactor["address"]}
                messages.append(react)

    return {"chats": chats, "messages": messages, "atts": atts}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--chats", type=int, default=30)
    ap.add_argument("--messages", type=int, default=300)
    ap.add_argument("--seed", type=int, default=7)
    args = ap.parse_args()

    payload = build(args.chats, args.messages, args.seed)
    blob = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    with open(args.out, "wb") as f:
        f.write(struct.pack(">I", len(blob)))
        f.write(blob)

    print(
        f"{args.out}: {len(payload['chats'])} chats, "
        f"{len(payload['messages'])} rows, {len(blob) / 1e6:.1f} MB"
    )


if __name__ == "__main__":
    main()
