package tenant

func Configure(issuers []string) {
	validIssuers = make(map[string]bool, len(issuers))
	for _, issuer := range issuers {
		if issuer != "" {
			validIssuers[issuer] = true
		}
	}
}

func IsValid(issuer string) bool {
	_, ok := validIssuers[issuer]
	return ok
}

var validIssuers = map[string]bool{
	"tenant-a": true,
	"tenant-b": true,
}
