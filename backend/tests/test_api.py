from conftest import login


def test_health_and_bundle(client):
    r = client.get("/health")
    assert r.status_code == 200 and r.json()["ok"] is True
    assert client.get("/locations/version").json()["version"] == "test-1"
    r = client.get("/locations/bundle", headers={"Accept-Encoding": "identity"})
    assert r.status_code == 200
    body = r.json()
    assert body["meta"]["version"] == "test-1"
    assert [v for v in body["villages"] if v[0] == 100] == [[100, 4512, "Godavarru", "గొడవర్రు"]]
    r304 = client.get("/locations/bundle", headers={"If-None-Match": r.headers["ETag"]})
    assert r304.status_code == 304


def test_search_villages(client):
    hits = client.get("/search/villages", params={"q": "god"}).json()
    assert hits and hits[0]["name"] == "Godavarru" and hits[0]["constituency_name"] == "Ponnur"


def test_login_and_me(client):
    h = login(client, "alice", "Alice", "alice@example.com")
    me = client.get("/me", headers=h).json()
    assert me["name"] == "Alice" and me["role"] == "user"
    r = client.patch("/me", json={"home_village_id": 100}, headers=h)
    assert r.status_code == 200 and r.json()["home_village_id"] == 100
    assert client.patch("/me", json={"home_village_id": 999}, headers=h).status_code == 422
    assert client.get("/me").status_code == 401
    admin = login(client, "boss", "Boss", "admin@example.com")
    assert client.get("/me", headers=admin).json()["role"] == "admin"


def test_problem_lifecycle(client):
    alice = login(client, "alice")
    bob = login(client, "bob", "Bob")
    # anonymous cannot post
    assert client.post("/problems", json={"mandal_id": 4512, "category": "road", "title": "Road is broken", "description": "Potholes near the temple"}).status_code == 401
    # village must belong to mandal
    bad = client.post("/problems", json={"mandal_id": 4513, "village_id": 100, "category": "road", "title": "Road is broken", "description": "x" * 10}, headers=alice)
    assert bad.status_code == 422
    # photo must be from our Cloudinary
    bad = client.post("/problems", json={"mandal_id": 4512, "village_id": 100, "category": "road", "title": "Road is broken", "description": "x" * 10, "photo_url": "https://evil.example/x.jpg"}, headers=alice)
    assert bad.status_code == 422
    r = client.post("/problems", json={"mandal_id": 4512, "village_id": 100, "category": "road", "title": "Road is broken", "description": "Potholes near the temple", "photo_url": "https://res.cloudinary.com/demo/image/upload/v1/problems/a.jpg"}, headers=alice)
    assert r.status_code == 201, r.text
    p = r.json()
    assert p["constituency_id"] == 96 and p["is_owner"] is True and p["status"] == "open"
    pid = p["id"]

    # feeds
    assert [x["id"] for x in client.get("/problems", params={"village_id": 100}).json()["items"]] == [pid]
    assert [x["id"] for x in client.get("/problems", params={"mandal_id": 4512}).json()["items"]] == [pid]
    assert [x["id"] for x in client.get("/problems", params={"constituency_id": 96}).json()["items"]] == [pid]
    assert client.get("/problems", params={"village_id": 101}).json()["items"] == []
    assert client.get("/problems").status_code == 422
    assert client.get("/problems", params={"village_id": 100, "mandal_id": 4512}).status_code == 422

    # upvote toggle
    r = client.post(f"/problems/{pid}/upvote", headers=bob).json()
    assert r == {"upvoted": True, "upvote_count": 1}
    assert client.get(f"/problems/{pid}", headers=bob).json()["my_upvote"] is True
    assert client.post(f"/problems/{pid}/upvote", headers=bob).json() == {"upvoted": False, "upvote_count": 0}

    # comments
    r = client.post(f"/problems/{pid}/comments", json={"body": "Same here"}, headers=bob)
    assert r.status_code == 201 and r.json()["author"]["name"] == "Bob"
    comments = client.get(f"/problems/{pid}/comments").json()
    assert [c["body"] for c in comments["items"]] == ["Same here"] and comments["next_cursor"] is None
    assert client.get(f"/problems/{pid}").json()["comment_count"] == 1

    # editing rights
    assert client.patch(f"/problems/{pid}", json={"status": "resolved"}, headers=bob).status_code == 403
    assert client.patch(f"/problems/{pid}", json={"status": "in_progress"}, headers=alice).status_code == 403
    assert client.patch(f"/problems/{pid}", json={"status": "resolved"}, headers=alice).json()["status"] == "resolved"
    admin = login(client, "boss", "Boss", "admin@example.com")
    assert client.patch(f"/problems/{pid}", json={"status": "in_progress"}, headers=admin).json()["status"] == "in_progress"


def test_pagination_new_and_top(client):
    carol = login(client, "carol", "Carol")
    ids = []
    for i in range(4):
        r = client.post("/problems", json={"mandal_id": 4513, "village_id": 102, "category": "water", "title": f"Water problem {i}", "description": "No water for days"}, headers=carol)
        ids.append(r.json()["id"])
    for voter in ("v1", "v2"):
        client.post(f"/problems/{ids[1]}/upvote", headers=login(client, voter))
    client.post(f"/problems/{ids[2]}/upvote", headers=login(client, "v3"))
    page1 = client.get("/problems", params={"village_id": 102, "limit": 3}).json()
    assert [x["id"] for x in page1["items"]] == ids[::-1][:3] and page1["next_cursor"]
    page2 = client.get("/problems", params={"village_id": 102, "limit": 3, "cursor": page1["next_cursor"]}).json()
    assert [x["id"] for x in page2["items"]] == [ids[0]] and page2["next_cursor"] is None
    top = client.get("/problems", params={"village_id": 102, "sort": "top", "limit": 2}).json()
    assert [x["id"] for x in top["items"]] == [ids[1], ids[2]]
    top2 = client.get("/problems", params={"village_id": 102, "sort": "top", "limit": 2, "cursor": top["next_cursor"]}).json()
    assert [x["id"] for x in top2["items"]] == [ids[3], ids[0]]
    assert client.get("/problems", params={"village_id": 102, "cursor": "garbage"}).status_code == 422


def test_reports_auto_hide_and_blocks(client):
    dave = login(client, "dave", "Dave")
    r = client.post("/problems", json={"mandal_id": 4512, "village_id": 101, "category": "garbage", "title": "Spam spam", "description": "buy now"}, headers=dave)
    pid = r.json()["id"]
    assert client.post(f"/problems/{pid}/report", json={"reason": "spam"}, headers=dave).status_code == 422  # own post
    for i, who in enumerate(("r1", "r2")):
        assert client.post(f"/problems/{pid}/report", json={"reason": "spam"}, headers=login(client, who)).json()["already"] is False
    assert client.post(f"/problems/{pid}/report", json={"reason": "spam"}, headers=login(client, "r1")).json()["already"] is True
    assert client.get(f"/problems/{pid}").status_code == 200
    client.post(f"/problems/{pid}/report", json={"reason": "abuse"}, headers=login(client, "r3"))
    assert client.get(f"/problems/{pid}").status_code == 404  # auto-hidden after 3 reports
    assert client.get(f"/problems/{pid}", headers=dave).status_code == 200  # owner still sees it
    admin = login(client, "boss", "Boss", "admin@example.com")
    reports = client.get("/admin/reports", headers=admin).json()
    assert any(x["target_id"] == pid and x["target_hidden"] for x in reports)
    rid = next(x["id"] for x in reports if x["target_id"] == pid)
    assert client.post(f"/admin/reports/{rid}/resolve", json={"action": "unhide"}, headers=admin).status_code == 200
    assert client.get(f"/problems/{pid}").status_code == 200
    assert client.get("/admin/reports", headers=dave).status_code == 403

    # blocking hides that author's posts for the blocker only
    erin = login(client, "erin", "Erin")
    assert client.post(f"/users/{client.get('/me', headers=dave).json()['id']}/block", headers=erin).json()["blocked"] is True
    assert pid not in [x["id"] for x in client.get("/problems", params={"village_id": 101}, headers=erin).json()["items"]]
    assert pid in [x["id"] for x in client.get("/problems", params={"village_id": 101}).json()["items"]]


def test_delete_account_hides_content(client):
    frank = login(client, "frank", "Frank")
    pid = client.post("/problems", json={"mandal_id": 4512, "village_id": 101, "category": "health", "title": "No doctor at PHC", "description": "Closed for a week"}, headers=frank).json()["id"]
    assert client.delete("/me", headers=frank).status_code == 204
    assert client.get(f"/problems/{pid}").status_code == 404
    assert client.get("/me", headers=frank).json()["name"] == "Deleted user"


def test_dev_login_disabled_in_production(client, monkeypatch):
    from app import config
    monkeypatch.setattr(config.settings, "env", "production")
    assert client.post("/auth/dev", json={"sub": "x"}).status_code == 404


def test_firebase_login(client, monkeypatch):
    from app import security
    monkeypatch.setattr(security, "verify_firebase_id_token", lambda tok: {"sub": "uid123", "email": "fb@example.com", "name": "Firebase User", "picture": None})
    r = client.post("/auth/firebase", json={"id_token": "x" * 40})
    assert r.status_code == 200, r.text
    assert r.json()["user"]["name"] == "Firebase User"
    me = client.get("/me", headers={"Authorization": f"Bearer {r.json()['access_token']}"}).json()
    assert me["email"] == "fb@example.com"
    again = client.post("/auth/firebase", json={"id_token": "x" * 40}).json()
    assert again["user"]["id"] == r.json()["user"]["id"]  # same Firebase uid -> same account


def test_firebase_login_rejects_bad_token(client, monkeypatch):
    from app import security

    def boom(tok):
        raise ValueError("bad token")

    monkeypatch.setattr(security, "verify_firebase_id_token", boom)
    assert client.post("/auth/firebase", json={"id_token": "x" * 40}).status_code == 401
