package ficha

import "testing"

func TestIsValidISODate(t *testing.T) {
	tests := []struct {
		name  string
		value string
		valid bool
	}{
		{name: "valid date", value: "2026-05-13", valid: true},
		{name: "empty", value: "", valid: false},
		{name: "wrong format", value: "13-05-2026", valid: false},
		{name: "invalid calendar day", value: "2026-02-30", valid: false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := isValidISODate(tt.value); got != tt.valid {
				t.Fatalf("isValidISODate(%q) = %v, want %v", tt.value, got, tt.valid)
			}
		})
	}
}
