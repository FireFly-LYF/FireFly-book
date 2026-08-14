package auth

import (
	"testing"
)

func TestSignInternalStable(t *testing.T) {
	secret := []byte("firefly-internal-hmac-dev-change-me!")
	got := SignInternal(secret, "42", 1_700_000_000)
	want := SignInternal(secret, "42", 1_700_000_000)
	if got != want {
		t.Fatalf("unstable sign")
	}
	if !VerifyInternal(secret, "42", got, 1_700_000_000) {
		t.Fatalf("verify failed")
	}
	if VerifyInternal(secret, "43", got, 1_700_000_000) {
		t.Fatalf("wrong uid should fail")
	}
	if VerifyInternal(secret, "42", got, 1_700_000_001) {
		t.Fatalf("wrong ts should fail")
	}
	empty := SignInternal(secret, "", 1_700_000_000)
	if !VerifyInternal(secret, "", empty, 1_700_000_000) {
		t.Fatalf("empty uid verify failed")
	}
}
