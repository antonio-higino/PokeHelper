package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
)

var errInvalid = errors.New("invalid argument")
var errNotFound = errors.New("resource not found")
var errUnavailable = errors.New("catalog upstream unavailable")

func (s *Server) lookup(ctx context.Context, resource, identifier string) (map[string]any, error) {
	id, err := safeIdentifier(identifier)
	if err != nil {
		return nil, fmt.Errorf("%w: %s", errInvalid, err)
	}
	body, code, err := s.client.GetContext(ctx, resource+url.PathEscape(id))
	if err != nil {
		if ctx.Err() != nil {
			return nil, ctx.Err()
		}
		return nil, errUnavailable
	}
	if code == http.StatusNotFound {
		return nil, errNotFound
	}
	if code < 200 || code >= 300 {
		return nil, errUnavailable
	}
	var document map[string]any
	if err := json.Unmarshal(body, &document); err != nil || document == nil {
		return nil, errUnavailable
	}
	return document, nil
}

func (s *Server) listTypes(ctx context.Context) (map[string]any, error) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	type result struct {
		index int
		data  map[string]any
		err   error
	}
	results := make(chan result, len(expectedTypes))
	for i, name := range expectedTypes {
		go func(i int, name string) { data, err := s.lookup(ctx, "/type/", name); results <- result{i, data, err} }(i, name)
	}
	items := make([]any, len(expectedTypes))
	for range expectedTypes {
		select {
		case <-ctx.Done():
			return nil, ctx.Err()
		case r := <-results:
			if r.err != nil {
				return nil, r.err
			}
			items[r.index] = r.data
		}
	}
	return map[string]any{"items": items}, nil
}
