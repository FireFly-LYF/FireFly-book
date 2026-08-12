package middleware

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
)

func TestIsAPIInnerPath(t *testing.T) {
	cases := []struct {
		path string
		want bool
	}{
		{"/api/search/inner", true},
		{"/api/search/inner/index", true},
		{"/api/notify/inner/create", true},
		{"/api/foo/inner/bar", true},
		{"/api/search/note", false},
		{"/api/notify/list", false},
		{"/api/internal", false},
		{"/api/internal/secret", false},
		{"/files/x", false},
		{"/api/search", false},
	}
	for _, tc := range cases {
		if got := isAPIInnerPath(tc.path); got != tc.want {
			t.Fatalf("isAPIInnerPath(%q)=%v want %v", tc.path, got, tc.want)
		}
	}
}

func TestBlockListInnerAndPrefix(t *testing.T) {
	gin.SetMode(gin.TestMode)
	r := gin.New()
	r.Use(BlockList([]string{"/api/internal", "/api/search/inner", "/api/notify/inner"}))
	r.Any("/*path", func(c *gin.Context) { c.Status(http.StatusOK) })

	blocked := []string{
		"/api/internal/secret",
		"/api/search/inner/index",
		"/api/notify/inner/create",
		"/api/other/inner/x",
	}
	for _, path := range blocked {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, path, nil)
		r.ServeHTTP(w, req)
		if w.Code != http.StatusForbidden {
			t.Fatalf("%s: got %d want 403", path, w.Code)
		}
	}

	ok := []string{"/api/search/note", "/api/notify/list"}
	for _, path := range ok {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, path, nil)
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("%s: got %d want 200", path, w.Code)
		}
	}
}
