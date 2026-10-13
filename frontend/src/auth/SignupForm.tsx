import { useActionState } from 'react';
import { Link } from 'react-router';
import { createSession, createUser } from '../api/accounts';
import { Field } from '../components/Field';
import { useAuth } from './AuthContext';
import { readText } from '../lib/formData';
import { emptyFormState, failedFormState, type FormState } from './formState';

export function SignupForm() {
  const { signIn } = useAuth();

  const [state, submit, pending] = useActionState(async (_previous: FormState, formData: FormData) => {
    const username = readText(formData, 'username').trim();
    const email = readText(formData, 'email').trim();
    const birthday = readText(formData, 'birthday');
    const password = readText(formData, 'password');
    try {
      await createUser({ username, email, password, birthday });
      signIn(await createSession({ username, password }));
      return emptyFormState;
    } catch (error) {
      return failedFormState(error, { username, email, birthday });
    }
  }, emptyFormState);

  return (
    <form className="login-form" action={submit} aria-labelledby="signup-title">
      <h1 id="signup-title" className="login-form__title">
        Sign up
      </h1>
      {state.message !== null && (
        <p className="login-form__error" role="alert">
          {state.message}
        </p>
      )}
      <Field
        label="Username"
        name="username"
        autoComplete="username"
        autoCapitalize="none"
        required
        minLength={3}
        maxLength={30}
        pattern="[A-Za-z0-9_]+"
        title="3 to 30 letters, digits or underscores"
        hint="3 to 30 letters, digits or underscores."
        defaultValue={state.values.username}
        error={state.fields.username}
      />
      <Field
        label="Email"
        name="email"
        type="email"
        autoComplete="email"
        required
        defaultValue={state.values.email}
        error={state.fields.email}
      />
      <Field
        label="Password"
        name="password"
        type="password"
        autoComplete="new-password"
        required
        minLength={8}
        maxLength={128}
        hint="At least 8 characters."
        error={state.fields.password}
      />
      <Field
        label="Birthday"
        name="birthday"
        type="date"
        autoComplete="bday"
        required
        defaultValue={state.values.birthday}
        error={state.fields.birthday}
      />
      <button type="submit" className="login-form__button btn btn--white btn--animated" disabled={pending}>
        {pending ? 'Creating your account…' : 'Sign up'}
      </button>
      <p className="login-form__links">
        Have an account? <Link to="/login">Log in</Link>
      </p>
    </form>
  );
}
