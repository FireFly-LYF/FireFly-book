package middleware

import "testing"

func TestMatchPublicAPI(t *testing.T) {
	skip := map[string]struct{}{
		"POST /api/user/login":         {},
		"GET /api/feed/discover":       {},
		"GET /api/feed/nearby":         {},
		"GET /api/note/*":              {},
		"GET /api/note/user/*":         {},
		"GET /api/social/like/*/count": {},
		"POST /api/media/sign":         {},
		"GET /api/search/note":         {},
		"GET /api/search/user":         {},
	}

	cases := []struct {
		method, path string
		want         bool
	}{
		{"POST", "/api/user/login", true},
		{"GET", "/api/feed/discover", true},
		{"GET", "/api/feed/nearby", true},
		{"GET", "/api/note/42", true},
		{"GET", "/api/note/user/7", true},
		{"GET", "/api/social/like/9/count", true},
		{"POST", "/api/media/sign", true},
		{"GET", "/api/search/note", true},
		{"GET", "/api/search/user", true},
		{"POST", "/api/note", false},
		{"PUT", "/api/note/42", false},
		{"GET", "/api/social/like/9/me", false},
		{"GET", "/api/feed/following", false},
	}
	for _, tc := range cases {
		got := matchPublicAPI(skip, tc.method, tc.path)
		if got != tc.want {
			t.Fatalf("%s %s: got %v want %v", tc.method, tc.path, got, tc.want)
		}
	}
}
