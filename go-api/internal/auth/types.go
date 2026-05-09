package auth

type LoginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type RefreshTokenRequest struct {
	RefreshToken string `json:"refreshToken"`
}

type PasswordResetRequest struct {
	Email string `json:"email"`
}

type PasswordResetConfirmRequest struct {
	Token       string `json:"token"`
	NewPassword string `json:"newPassword"`
}

type LoginResponse struct {
	Token            string   `json:"token"`
	Email            string   `json:"email"`
	Nome             string   `json:"nome"`
	Roles            []string `json:"roles"`
	ExpiresIn        int64    `json:"expiresIn"`
	RefreshToken     string   `json:"refreshToken,omitempty"`
	RefreshExpiresIn int64    `json:"refreshExpiresIn,omitempty"`
}

type User struct {
	ID           int64
	Nome         string
	Email        string
	PasswordHash string
	Ativo        bool
	Roles        []string
}

type StoredRefreshToken struct {
	ID            int64
	UserID        int64
	TokenHash     string
	Revogado      bool
	DataExpiracao int64
}

type StoredPasswordResetToken struct {
	ID            int64
	Token         string
	UserID        int64
	Usado         bool
	DataExpiracao int64
}
